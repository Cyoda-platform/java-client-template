package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * ABOUTME: Example test demonstrating how to test ProductController endpoints
 * with mocked EntityService to verify controller behavior and response mapping.
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private EntityService entityService;

    @Mock
    private ObjectMapper objectMapper;

    private ProductController productController;

    @BeforeEach
    void setUp() {
        productController = new ProductController(entityService, objectMapper);
    }

    @Test
    @DisplayName("searchProducts should return products when found")
    void testSearchProductsSuccess() {
        // Given
        String search = "laptop";
        String category = "electronics";
        BigDecimal minPrice = BigDecimal.valueOf(100);
        BigDecimal maxPrice = BigDecimal.valueOf(1000);
        Pageable pageable = PageRequest.of(0, 10);

        // Create mock product
        Product mockProduct = createMockProduct();
        EntityWithMetadata<Product> productWithMetadata = new EntityWithMetadata<>(mockProduct, createMockMetadata());

        // Mock EntityService response
        Page<EntityWithMetadata<Product>> mockPage = createMockPage(List.of(productWithMetadata));
        when(entityService.findEntitiesWithConditions(
                eq(Product.class),
                any(List.class), // QueryCondition list
                eq(pageable)
        )).thenReturn(mockPage);

        // When
        ResponseEntity<Page<ProductController.ProductSlimDto>> response = productController.searchProducts(
                search, category, minPrice, maxPrice, pageable
        );

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        
        ProductController.ProductSlimDto slimDto = response.getBody().getContent().get(0);
        assertEquals("LAPTOP-001", slimDto.getSku());
        assertEquals("Gaming Laptop", slimDto.getName());
        assertEquals("electronics", slimDto.getCategory());
        assertEquals(BigDecimal.valueOf(899.99), slimDto.getPrice());
        assertEquals(50, slimDto.getQuantityAvailable());
    }

    @Test
    @DisplayName("searchProducts should return empty page when no products found")
    void testSearchProductsEmpty() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<EntityWithMetadata<Product>> emptyPage = createMockPage(List.of());
        
        when(entityService.findEntitiesWithConditions(
                eq(Product.class),
                any(List.class),
                eq(pageable)
        )).thenReturn(emptyPage);

        // When
        ResponseEntity<Page<ProductController.ProductSlimDto>> response = productController.searchProducts(
                null, null, null, null, pageable
        );

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getTotalElements());
        assertTrue(response.getBody().getContent().isEmpty());
    }

    private Product createMockProduct() {
        Product product = new Product();
        product.setSku("LAPTOP-001");
        product.setName("Gaming Laptop");
        product.setDescription("High-performance gaming laptop");
        product.setPrice(BigDecimal.valueOf(899.99));
        product.setQuantityAvailable(50);
        product.setCategory("electronics");
        product.setWarehouseId("WH-001");
        return product;
    }

    private EntityMetadata createMockMetadata() {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(UUID.randomUUID());
        metadata.setState("ACTIVE");
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private Page<EntityWithMetadata<Product>> createMockPage(List<EntityWithMetadata<Product>> content) {
        return new org.springframework.data.domain.PageImpl<>(content, PageRequest.of(0, 10), content.size());
    }
}
