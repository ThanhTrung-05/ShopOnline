package com.example.banhangtructuyen.application.service;

import com.example.banhangtructuyen.application.dto.product.ProductDetailResponse;
import com.example.banhangtructuyen.application.service.impl.ProductServiceImpl;
import com.example.banhangtructuyen.config.AppProperties;
import com.example.banhangtructuyen.domain.exception.ResourceNotFoundException;
import com.example.banhangtructuyen.domain.model.Category;
import com.example.banhangtructuyen.domain.model.Inventory;
import com.example.banhangtructuyen.domain.model.Product;
import com.example.banhangtructuyen.domain.repository.ProductRepository;
import com.example.banhangtructuyen.infrastructure.cache.CacheKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductDetailServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product mockProduct;
    private ProductDetailResponse mockDetailResponse;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setCategoryId(1L);
        category.setCategoryName("Dairy");
        category.setVatRate(new BigDecimal("5.00")); // 5% VAT

        Inventory inventory = new Inventory();
        inventory.setQuantity(100);
        inventory.setReservedQuantity(20);

        mockProduct = new Product();
        mockProduct.setProductId(1L);
        mockProduct.setProductName("Milk");
        mockProduct.setProductSlug("milk");
        mockProduct.setPrice(new BigDecimal("20000"));
        mockProduct.setCategory(category);
        mockProduct.setInventory(inventory);
        mockProduct.setStatus(Product.ProductStatus.ACTIVE);

        mockDetailResponse = new ProductDetailResponse(
                1L, "Milk", "milk", new BigDecimal("20000"),
                new BigDecimal("5.00"), new BigDecimal("1000"), new BigDecimal("21000"),
                null, null, 1L, "Dairy", 80, "ACTIVE"
        );
    }

    @Test
    void findDetailById_CacheHit_ReturnsFromCache() throws Exception {
        String cacheKey = CacheKeys.productDetail(1L) + ":detail";
        
        AppProperties.Redis redisProps = new AppProperties.Redis();
        AppProperties.Ttl ttl = new AppProperties.Ttl();
        ttl.setProductDetail(600);
        redisProps.setTtl(ttl);
        lenient().when(appProperties.getRedis()).thenReturn(redisProps);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn("{\"id\":1}");
        when(objectMapper.readValue("{\"id\":1}", ProductDetailResponse.class)).thenReturn(mockDetailResponse);

        ProductDetailResponse result = productService.findDetailById(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("21000"), result.priceIncludingVat());
        verify(productRepository, never()).findActiveById(anyLong());
    }

    @Test
    void findDetailById_CacheMiss_QueriesDBAndCaches_CalculatesVatCorrectly() throws Exception {
        String cacheKey = CacheKeys.productDetail(1L) + ":detail";
        
        AppProperties.Redis redisProps = new AppProperties.Redis();
        AppProperties.Ttl ttl = new AppProperties.Ttl();
        ttl.setProductDetail(600);
        redisProps.setTtl(ttl);
        when(appProperties.getRedis()).thenReturn(redisProps);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(productRepository.findActiveById(1L)).thenReturn(Optional.of(mockProduct));
        when(objectMapper.writeValueAsString(any(ProductDetailResponse.class))).thenReturn("{}");

        ProductDetailResponse result = productService.findDetailById(1L);

        assertNotNull(result);
        assertEquals(new BigDecimal("20000"), result.price());
        assertEquals(new BigDecimal("5.00"), result.vatRate());
        assertEquals(new BigDecimal("1000"), result.vatAmount());
        assertEquals(new BigDecimal("21000"), result.priceIncludingVat());

        verify(valueOperations).set(eq(cacheKey), eq("{}"), any());
    }

    @Test
    void findDetailById_NotFound_ThrowsException() {
        String cacheKey = CacheKeys.productDetail(99L) + ":detail";
        
        AppProperties.Redis redisProps = new AppProperties.Redis();
        AppProperties.Ttl ttl = new AppProperties.Ttl();
        ttl.setProductDetail(600);
        redisProps.setTtl(ttl);
        lenient().when(appProperties.getRedis()).thenReturn(redisProps);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(productRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.findDetailById(99L));
    }
}
