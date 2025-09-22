package com.java_template.application;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to validate the OMS system components work together.
 * Tests entity creation and basic functionality.
 */
@SpringBootTest
@ActiveProfiles("test")
public class OmsIntegrationTest {

    @Autowired
    private EntityService entityService;

    @Test
    public void testProductCreation() {
        // Create a test product
        Product product = new Product();
        product.setSku("TEST-SKU-001");
        product.setName("Test Product");
        product.setDescription("A test product for integration testing");
        product.setPrice(99.99);
        product.setQuantityAvailable(100);
        product.setCategory("Electronics");

        // Test product validation
        assertTrue(product.isValid(), "Product should be valid");

        // Test entity creation
        EntityWithMetadata<Product> createdProduct = entityService.create(product);
        assertNotNull(createdProduct, "Created product should not be null");
        assertNotNull(createdProduct.metadata().getId(), "Product should have technical ID");
        assertEquals("TEST-SKU-001", createdProduct.entity().getSku(), "SKU should match");

        // Test retrieval by business ID
        ModelSpec modelSpec = new ModelSpec()
                .withName(Product.ENTITY_NAME)
                .withVersion(Product.ENTITY_VERSION);

        EntityWithMetadata<Product> retrievedProduct = entityService.findByBusinessId(
                modelSpec, "TEST-SKU-001", "sku", Product.class);

        assertNotNull(retrievedProduct, "Retrieved product should not be null");
        assertEquals("TEST-SKU-001", retrievedProduct.entity().getSku(), "Retrieved SKU should match");
    }

    @Test
    public void testCartCreation() {
        // Create a test cart
        Cart cart = new Cart();
        cart.setCartId("TEST-CART-001");
        cart.setStatus("NEW");
        cart.setLines(new ArrayList<>());
        cart.setTotalItems(0);
        cart.setGrandTotal(0.0);
        cart.setCreatedAt(LocalDateTime.now());
        cart.setUpdatedAt(LocalDateTime.now());

        // Test cart validation
        assertTrue(cart.isValid(), "Cart should be valid");

        // Test entity creation
        EntityWithMetadata<Cart> createdCart = entityService.create(cart);
        assertNotNull(createdCart, "Created cart should not be null");
        assertNotNull(createdCart.metadata().getId(), "Cart should have technical ID");
        assertEquals("TEST-CART-001", createdCart.entity().getCartId(), "Cart ID should match");

        // Test retrieval by business ID
        ModelSpec modelSpec = new ModelSpec()
                .withName(Cart.ENTITY_NAME)
                .withVersion(Cart.ENTITY_VERSION);

        EntityWithMetadata<Cart> retrievedCart = entityService.findByBusinessId(
                modelSpec, "TEST-CART-001", "cartId", Cart.class);

        assertNotNull(retrievedCart, "Retrieved cart should not be null");
        assertEquals("TEST-CART-001", retrievedCart.entity().getCartId(), "Retrieved cart ID should match");
    }

    @Test
    public void testEntityValidation() {
        // Test invalid product
        Product invalidProduct = new Product();
        // Missing required fields
        assertFalse(invalidProduct.isValid(), "Product without required fields should be invalid");

        // Test invalid cart
        Cart invalidCart = new Cart();
        // Missing required fields
        assertFalse(invalidCart.isValid(), "Cart without required fields should be invalid");
    }
}
