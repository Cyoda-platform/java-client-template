package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.java_template.application.entity.Product;
import com.java_template.application.entity.Cart;
import com.java_template.application.entity.Customer;
import com.java_template.application.entity.Order;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Product> productCache = new ConcurrentHashMap<>();
    private final AtomicLong productIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Cart> cartCache = new ConcurrentHashMap<>();
    private final AtomicLong cartIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Customer> customerCache = new ConcurrentHashMap<>();
    private final AtomicLong customerIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Order> orderCache = new ConcurrentHashMap<>();
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    // PRODUCT endpoints

    @PostMapping("/product")
    public ResponseEntity<Map<String, String>> createProduct(@RequestBody Product product) {
        if (!product.isValid()) {
            log.error("Invalid product data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "product-" + productIdCounter.getAndIncrement();
        productCache.put(technicalId, product);
        processProduct(technicalId, product);
        log.info("Product created with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/product/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable String id) {
        Product product = productCache.get(id);
        if (product == null) {
            log.error("Product not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(product);
    }

    // CART endpoints

    @PostMapping("/cart")
    public ResponseEntity<Map<String, String>> createCart(@RequestBody Cart cart) {
        if (!cart.isValid()) {
            log.error("Invalid cart data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "cart-" + cartIdCounter.getAndIncrement();
        cartCache.put(technicalId, cart);
        processCart(technicalId, cart);
        log.info("Cart created with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/cart/{id}")
    public ResponseEntity<Cart> getCart(@PathVariable String id) {
        Cart cart = cartCache.get(id);
        if (cart == null) {
            log.error("Cart not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(cart);
    }

    // CUSTOMER endpoints

    @PostMapping("/customer")
    public ResponseEntity<Map<String, String>> createCustomer(@RequestBody Customer customer) {
        if (!customer.isValid()) {
            log.error("Invalid customer data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "customer-" + customerIdCounter.getAndIncrement();
        customerCache.put(technicalId, customer);
        processCustomer(technicalId, customer);
        log.info("Customer created with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/customer/{id}")
    public ResponseEntity<Customer> getCustomer(@PathVariable String id) {
        Customer customer = customerCache.get(id);
        if (customer == null) {
            log.error("Customer not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(customer);
    }

    // ORDER endpoints

    @PostMapping("/order")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Order order) {
        if (!order.isValid()) {
            log.error("Invalid order data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "order-" + orderIdCounter.getAndIncrement();
        orderCache.put(technicalId, order);
        processOrder(technicalId, order);
        log.info("Order created with technicalId: {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/order/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        Order order = orderCache.get(id);
        if (order == null) {
            log.error("Order not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(order);
    }

    // PROCESS METHODS

    private void processProduct(String technicalId, Product product) {
        // Business logic:
        // Validate SKU uniqueness (simplified example)
        long count = productCache.values().stream()
                .filter(p -> p.getSku().equals(product.getSku())).count();
        if (count > 1) {
            log.error("Duplicate SKU detected for product: {}", product.getSku());
        }
        // Log product creation
        log.info("Processed product with SKU: {}", product.getSku());
    }

    private void processCart(String technicalId, Cart cart) {
        // Business logic:
        // Validate product SKUs exist in product cache
        boolean allSkusValid = cart.getItems().stream()
                .allMatch(line -> productCache.values().stream()
                        .anyMatch(p -> p.getSku().equals(line.getSku())));
        if (!allSkusValid) {
            log.error("Cart contains invalid product SKUs");
        }
        // Calculate totals
        int totalItems = cart.getItems().stream().mapToInt(line -> line.getQuantity()).sum();
        double grandTotal = cart.getItems().stream().mapToDouble(line -> line.getQuantity() * line.getPrice()).sum();
        cart.setTotalItems(totalItems);
        cart.setGrandTotal(grandTotal);
        // Log cart processing
        log.info("Processed cart with total items: {} and grand total: {}", totalItems, grandTotal);
    }

    private void processCustomer(String technicalId, Customer customer) {
        // Business logic:
        // Validate email uniqueness (simplified example)
        long count = customerCache.values().stream()
                .filter(c -> c.getEmail().equalsIgnoreCase(customer.getEmail())).count();
        if (count > 1) {
            log.error("Duplicate email detected for customer: {}", customer.getEmail());
        }
        log.info("Processed customer with email: {}", customer.getEmail());
    }

    private void processOrder(String technicalId, Order order) {
        // Business logic:
        // Validate customer exists
        boolean customerExists = customerCache.values().stream()
                .anyMatch(c -> c.getCustomerId().equals(order.getCustomerId()));
        if (!customerExists) {
            log.error("Order references non-existent customer: {}", order.getCustomerId());
        }
        // Validate all SKUs exist
        boolean allSkusValid = order.getItems().stream()
                .allMatch(line -> productCache.values().stream()
                        .anyMatch(p -> p.getSku().equals(line.getSku())));
        if (!allSkusValid) {
            log.error("Order contains invalid product SKUs");
        }
        // Calculate total amount
        double totalAmount = order.getItems().stream()
                .mapToDouble(line -> line.getQuantity() * line.getPrice()).sum();
        order.setTotalAmount(totalAmount);
        // Log order processing
        log.info("Processed order with total amount: {}", totalAmount);
    }
}