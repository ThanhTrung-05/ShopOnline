package com.example.banhangtructuyen.application.service.impl;

import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.dto.product.ProductResponse;
import com.example.banhangtructuyen.application.service.ProductService;
import com.example.banhangtructuyen.config.AppProperties;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Product;
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

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    @Override
    public Page<ProductResponse> findAll(final int page, final int size,
                                         final String categoryCode, final String search) {
        final String cacheKey = CacheKeys.productList(page, size, categoryCode, search);
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
                .findActiveProducts(categoryCode, search, pageable);
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

    /** Evict product cache after updates. */
    public void evictCache(final Long productId) {
        redisTemplate.delete(CacheKeys.productDetail(productId));
        redisTemplate.delete(CacheKeys.productDetail(productId) + ":detail");
        log.debug("Product cache evicted: productId={}", productId);
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
