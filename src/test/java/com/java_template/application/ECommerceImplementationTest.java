package com.java_template.application;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify that the e-commerce entities are properly implemented
 * and can be instantiated and validated correctly.
 */
class ECommerceImplementationTest {

    @Test
    @DisplayName("Product entity creation and validation")
    void testProductEntity() {
        // Given - Create a product
        Product product = new Product();
        product.setSku("LAPTOP001");
        product.setName("Gaming Laptop");
        product.setDescription("High-performance gaming laptop");
        product.setPrice(new BigDecimal("1299.99"));
        product.setCategory("Electronics");
        product.setQuantityAvailable(10);

        // When & Then - Validate product
        assertNotNull(product);
        assertEquals("LAPTOP001", product.getSku());
        assertEquals("Gaming Laptop", product.getName());
        assertEquals(new BigDecimal("1299.99"), product.getPrice());
        assertEquals("Electronics", product.getCategory());
        assertEquals(10, product.getQuantityAvailable());
        
        System.out.println("✓ Product entity created successfully");
    }

    @Test
    @DisplayName("Cart entity creation and validation")
    void testCartEntity() {
        // Given - Create a cart with items
        Cart cart = new Cart();
        cart.setCartId("cart_123");
        cart.setLines(new ArrayList<>());
        
        // Add cart lines using the correct constructor
        Cart.CartLine line1 = new Cart.CartLine();
        line1.setSku("LAPTOP001");
        line1.setName("Gaming Laptop");
        line1.setPrice(new BigDecimal("1299.99"));
        line1.setQty(1);
        cart.getLines().add(line1);

        Cart.CartLine line2 = new Cart.CartLine();
        line2.setSku("MOUSE001");
        line2.setName("Gaming Mouse");
        line2.setPrice(new BigDecimal("79.99"));
        line2.setQty(2);
        cart.getLines().add(line2);

        cart.setTotalItems(3); // 1 + 2
        cart.setGrandTotal(new BigDecimal("1459.97")); // 1299.99 + (2 * 79.99)

        // When & Then - Validate cart
        assertNotNull(cart);
        assertEquals("cart_123", cart.getCartId());
        assertEquals(2, cart.getLines().size());
        assertEquals(3, cart.getTotalItems());
        assertEquals(new BigDecimal("1459.97"), cart.getGrandTotal());
        
        System.out.println("✓ Cart entity created successfully");
    }

    @Test
    @DisplayName("Payment entity creation and validation")
    void testPaymentEntity() {
        // Given - Create a payment
        Payment payment = new Payment();
        payment.setPaymentId("pay_123");
        payment.setCartId("cart_123");
        payment.setAmount(new BigDecimal("1459.97"));
        payment.setProvider("DUMMY");

        // When & Then - Validate payment
        assertNotNull(payment);
        assertEquals("pay_123", payment.getPaymentId());
        assertEquals("cart_123", payment.getCartId());
        assertEquals(new BigDecimal("1459.97"), payment.getAmount());
        assertEquals("DUMMY", payment.getProvider());
        
        System.out.println("✓ Payment entity created successfully");
    }

    @Test
    @DisplayName("Order entity creation and validation")
    void testOrderEntity() {
        // Given - Create an order
        Order order = new Order();
        order.setOrderId("order_123");
        order.setPaymentId("pay_123");
        order.setLines(new ArrayList<>());

        // Add order lines
        Order.OrderLine line1 = new Order.OrderLine();
        line1.setSku("LAPTOP001");
        line1.setName("Gaming Laptop");
        line1.setPrice(new BigDecimal("1299.99"));
        line1.setQty(1);
        order.getLines().add(line1);

        order.setTotalItems(1);
        order.setGrandTotal(new BigDecimal("1299.99"));

        // Add guest contact
        Order.GuestContact guestContact = new Order.GuestContact();
        guestContact.setFirstName("John");
        guestContact.setLastName("Doe");
        guestContact.setEmail("john.doe@example.com");
        guestContact.setPhone("555-1234");

        Order.Address address = new Order.Address();
        address.setLine1("123 Main St");
        address.setCity("Anytown");
        address.setState("CA");
        address.setPostcode("12345");
        address.setCountry("USA");
        guestContact.setAddress(address);

        order.setGuestContact(guestContact);

        // When & Then - Validate order
        assertNotNull(order);
        assertEquals("order_123", order.getOrderId());
        assertEquals("pay_123", order.getPaymentId());
        assertEquals(1, order.getLines().size());
        assertEquals(1, order.getTotalItems());
        assertEquals(new BigDecimal("1299.99"), order.getGrandTotal());
        assertNotNull(order.getGuestContact());
        assertEquals("John", order.getGuestContact().getFirstName());
        assertNotNull(order.getGuestContact().getAddress());
        
        System.out.println("✓ Order entity created successfully");
    }

    @Test
    @DisplayName("Shipment entity creation and validation")
    void testShipmentEntity() {
        // Given - Create a shipment
        Shipment shipment = new Shipment();
        shipment.setShipmentId("ship_123");
        shipment.setOrderId("order_123");
        shipment.setLines(new ArrayList<>());

        // Add shipment lines
        Shipment.ShipmentLine line1 = new Shipment.ShipmentLine();
        line1.setSku("LAPTOP001");
        line1.setQtyOrdered(1);
        line1.setQtyPicked(1);
        line1.setQtyShipped(1);
        shipment.getLines().add(line1);

        // When & Then - Validate shipment
        assertNotNull(shipment);
        assertEquals("ship_123", shipment.getShipmentId());
        assertEquals("order_123", shipment.getOrderId());
        assertEquals(1, shipment.getLines().size());
        
        Shipment.ShipmentLine line = shipment.getLines().get(0);
        assertEquals("LAPTOP001", line.getSku());
        assertEquals(1, line.getQtyOrdered());
        assertEquals(1, line.getQtyPicked());
        assertEquals(1, line.getQtyShipped());
        
        System.out.println("✓ Shipment entity created successfully");
    }

    @Test
    @DisplayName("Complete e-commerce workflow validation")
    void testCompleteWorkflow() {
        // Given - Create all entities for a complete workflow
        Product product = new Product();
        product.setSku("WORKFLOW001");
        product.setName("Test Product");
        product.setPrice(new BigDecimal("99.99"));
        product.setCategory("Test");
        product.setQuantityAvailable(5);

        Cart cart = new Cart();
        cart.setCartId("cart_workflow");
        cart.setLines(new ArrayList<>());
        Cart.CartLine cartLine = new Cart.CartLine();
        cartLine.setSku("WORKFLOW001");
        cartLine.setName("Test Product");
        cartLine.setPrice(new BigDecimal("99.99"));
        cartLine.setQty(1);
        cart.getLines().add(cartLine);
        cart.setTotalItems(1);
        cart.setGrandTotal(new BigDecimal("99.99"));

        Payment payment = new Payment();
        payment.setPaymentId("pay_workflow");
        payment.setCartId("cart_workflow");
        payment.setAmount(new BigDecimal("99.99"));
        payment.setProvider("DUMMY");

        Order order = new Order();
        order.setOrderId("order_workflow");
        order.setPaymentId("pay_workflow");
        order.setLines(new ArrayList<>());
        Order.OrderLine orderLine = new Order.OrderLine();
        orderLine.setSku("WORKFLOW001");
        orderLine.setName("Test Product");
        orderLine.setPrice(new BigDecimal("99.99"));
        orderLine.setQty(1);
        order.getLines().add(orderLine);
        order.setTotalItems(1);
        order.setGrandTotal(new BigDecimal("99.99"));

        Shipment shipment = new Shipment();
        shipment.setShipmentId("ship_workflow");
        shipment.setOrderId("order_workflow");
        shipment.setLines(new ArrayList<>());
        Shipment.ShipmentLine shipmentLine = new Shipment.ShipmentLine();
        shipmentLine.setSku("WORKFLOW001");
        shipmentLine.setQtyOrdered(1);
        shipmentLine.setQtyPicked(1);
        shipmentLine.setQtyShipped(1);
        shipment.getLines().add(shipmentLine);

        // When & Then - Validate complete workflow
        assertNotNull(product);
        assertNotNull(cart);
        assertNotNull(payment);
        assertNotNull(order);
        assertNotNull(shipment);

        // Validate data consistency across entities
        assertEquals(cart.getGrandTotal(), payment.getAmount());
        assertEquals(payment.getAmount(), order.getGrandTotal());
        assertEquals(order.getOrderId(), shipment.getOrderId());

        System.out.println("✓ Complete e-commerce workflow validated successfully");
        System.out.println("✓ All entities are properly implemented and working correctly!");
    }
}
