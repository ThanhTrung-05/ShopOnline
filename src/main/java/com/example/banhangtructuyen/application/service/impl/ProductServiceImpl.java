package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.dto.product.ProductRequest;
import com.example.banhangtructuyen.application.dto.product.ProductResponse;
import com.example.banhangtructuyen.application.service.ProductService;
import com.example.banhangtructuyen.config.AppProperties;
import com.example.banhangtructuyen.domain.exception.DuplicateResourceException;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.repository.CategoryRepository;
import com.example.banhangtructuyen.domain.repository.InventoryRepository;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import com.example.banhangtructuyen.infrastructure.cache.CacheKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    @Override
    public Page<ProductResponse> findAll(final int page, final int size,
                                         final Long categoryId, final String search,
                                         final BigDecimal minPrice, final BigDecimal maxPrice) {
        final String cacheKey = CacheKeys.productList(page, size, categoryId, search, minPrice, maxPrice);
        final int ttl = appProperties.getRedis().getTtl().getProductList();

        // Cache-Aside: try cache first
        try {
            final String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Cache HIT: {}", cacheKey);
                final Map<String, Object> map = objectMapper.readValue(cached,
                        new TypeReference<>() {});
                final List<ProductResponse> content = objectMapper.convertValue(
                        map.get("content"), new TypeReference<>() {});
                final long total = ((Number) map.get("total")).longValue();
                return new PageImpl<>(content, PageRequest.of(page, size), total);
            }
        } catch (final Exception e) {
            log.warn("Cache read error for key {}: {}", cacheKey, e.getMessage());
        }

        // Cache MISS → query DB
        log.debug("Cache MISS: {}", cacheKey);
        final Pageable pageable = PageRequest.of(page, size);
        final Page<Product> productPage = productRepository
                .findActiveProducts(categoryId, search, minPrice, maxPrice, pageable);
        final Page<ProductResponse> result = productPage.map(this::toResponse);

        // Populate cache
        try {
            final String json = objectMapper.writeValueAsString(
                    Map.of("content", result.getContent(), "total", result.getTotalElements()));
            redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(ttl));
        } catch (final Exception e) {
            log.warn("Cache write error for key {}: {}", cacheKey, e.getMessage());
        }

        return result;
    }

    @Override
    public ProductResponse findById(final Long productId) {
        final String cacheKey = CacheKeys.productDetail(productId);
        final int ttl = appProperties.getRedis().getTtl().getProductDetail();

        // Cache-Aside
        try {
            final String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Cache HIT: {}", cacheKey);
                return objectMapper.readValue(cached, ProductResponse.class);
            }
        } catch (final Exception e) {
            log.warn("Cache read error: {}", e.getMessage());
        }

        final Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        final ProductResponse response = toResponse(product);

        try {
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(ttl));
        } catch (final Exception e) {
            log.warn("Cache write error: {}", e.getMessage());
        }

        return response;
    }

    @Override
    public ProductDetailResponse findDetailById(final Long productId) {
        final String cacheKey = CacheKeys.productDetail(productId) + ":detail";
        final int ttl = appProperties.getRedis().getTtl().getProductDetail();

        // Cache-Aside: try cache first
        try {
            final String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Cache HIT (detail): {}", cacheKey);
                return objectMapper.readValue(cached, ProductDetailResponse.class);
            }
        } catch (final Exception e) {
            log.warn("Cache read error (detail): {}", e.getMessage());
        }

        // Cache MISS → query DB
        final Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        final ProductDetailResponse response = toDetailResponse(product);

        // Populate cache
        try {
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(response),
                    Duration.ofSeconds(ttl));
        } catch (final Exception e) {
            log.warn("Cache write error (detail): {}", e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional
    public ProductDetailResponse create(final ProductRequest request) {
        final Category category = getCategoryOrThrow(request.categoryId());

        if (productRepository.existsByProductSlug(request.productSlug())) {
            throw new DuplicateResourceException(
                    "Product slug already exists: " + request.productSlug());
        }

        final Product product = Product.builder()
                .productSlug(request.productSlug())
                .category(category)
                .productName(request.productName())
                .description(request.description())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .status(Product.ProductStatus.valueOf(request.status()))
                .build();
        final Product saved = productRepository.save(product);

        final Inventory inventory = Inventory.builder()
                .product(saved)
                .quantity(request.initialQuantity())
                .reservedQuantity(0)
                .build();
        inventoryRepository.save(inventory);
        saved.setInventory(inventory);

        evictListCache();
        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public ProductDetailResponse update(final Long productId, final ProductRequest request) {
        final Category category = getCategoryOrThrow(request.categoryId());
        final Product product = productRepository.findByIdWithInventory(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (productRepository.existsByProductSlugAndProductIdNot(request.productSlug(), productId)) {
            throw new DuplicateResourceException(
                    "Product slug already exists: " + request.productSlug());
        }

        product.setProductSlug(request.productSlug());
        product.setCategory(category);
        product.setProductName(request.productName());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setImageUrl(request.imageUrl());
        product.setStatus(Product.ProductStatus.valueOf(request.status()));

        if (product.getInventory() != null) {
            product.getInventory().setQuantity(request.initialQuantity());
        }

        final Product saved = productRepository.save(product);
        evictCache(productId);
        evictListCache();
        return toDetailResponse(saved);
    }

    @Override
    @Transactional
    public void delete(final Long productId) {
        final Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        product.setStatus(Product.ProductStatus.DELETED);
        productRepository.save(product);

        evictCache(productId);
        evictListCache();
    }

    private Category getCategoryOrThrow(final Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    /** Evict product cache after updates. */
    public void evictCache(final Long productId) {
        redisTemplate.delete(CacheKeys.productDetail(productId));
        redisTemplate.delete(CacheKeys.productDetail(productId) + ":detail");
        log.debug("Product cache evicted: productId={}", productId);
    }

    /** Evict all cached product list pages after a product is created/updated/deleted. */
    private void evictListCache() {
        final Set<String> keys = redisTemplate.keys("product:list:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /** Maps Product entity to lightweight ProductResponse (used by product list ATS-2). */
    private ProductResponse toResponse(final Product p) {
        return new ProductResponse(
                p.getProductId(),
                p.getProductName(),
                p.getProductSlug(),
                p.getPrice(),
                p.getImageUrl(),
                p.getDescription(),
                p.getCategory().getCategoryId(),
                p.getCategory().getCategoryName(),
                p.getInventory() != null ? p.getInventory().getAvailableQuantity() : 0,
                p.getStatus().name()
        );
    }

    /**
     * Maps Product entity to ProductDetailResponse with VAT breakdown (used by ATS-4).
     * vatAmount = price * vatRate / 100, rounded half-up to 0 decimal places.
     * priceIncludingVat = price + vatAmount.
     */
    private ProductDetailResponse toDetailResponse(final Product p) {
        final BigDecimal vatRate = p.getCategory().getVatRate() != null
                ? p.getCategory().getVatRate()
                : BigDecimal.TEN; // fallback 10% if data missing
        final BigDecimal vatAmount = p.getPrice()
                .multiply(vatRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        final BigDecimal priceIncludingVat = p.getPrice().add(vatAmount);

        return new ProductDetailResponse(
                p.getProductId(),
                p.getProductName(),
                p.getProductSlug(),
                p.getPrice(),
                vatRate,
                vatAmount,
                priceIncludingVat,
                p.getImageUrl(),
                p.getDescription(),
                p.getCategory().getCategoryId(),
                p.getCategory().getCategoryName(),
                p.getInventory() != null ? p.getInventory().getAvailableQuantity() : 0,
                p.getStatus().name()
        );
    }
}
