package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.*;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, UserImportJob> userImportJobCache = new ConcurrentHashMap<>();
    private final AtomicLong userImportJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, User> userCache = new ConcurrentHashMap<>();
    private final AtomicLong userIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Cart> cartCache = new ConcurrentHashMap<>();
    private final AtomicLong cartIdCounter = new AtomicLong(1);

    // UserImportJob endpoints
    @PostMapping("/userImportJob")
    public ResponseEntity<Map<String, String>> createUserImportJob(@RequestBody UserImportJob userImportJob) {
        String id = "userImportJob-" + userImportJobIdCounter.getAndIncrement();
        userImportJob.setJobId(id);
        userImportJob.setStatus("PENDING");
        userImportJob.setCreatedAt(java.time.LocalDateTime.now());

        if (!userImportJob.isValid()) {
            log.error("Invalid UserImportJob entity: {}", userImportJob);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        userImportJobCache.put(id, userImportJob);
        log.info("UserImportJob created with id {}", id);
        processUserImportJob(id, userImportJob);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/userImportJob/{id}")
    public ResponseEntity<UserImportJob> getUserImportJob(@PathVariable String id) {
        UserImportJob job = userImportJobCache.get(id);
        if (job == null) {
            log.error("UserImportJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // User endpoints
    @PostMapping("/user")
    public ResponseEntity<Map<String, String>> createUser(@RequestBody User user) {
        String id = "user-" + userIdCounter.getAndIncrement();
        user.setUserId(id);
        user.setCreatedAt(java.time.LocalDateTime.now());

        if (!user.isValid()) {
            log.error("Invalid User entity: {}", user);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        userCache.put(id, user);
        log.info("User created with id {}", id);
        processUser(id, user);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<User> getUser(@PathVariable String id) {
        User user = userCache.get(id);
        if (user == null) {
            log.error("User not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(user);
    }

    // Cart endpoints
    @PostMapping("/cart")
    public ResponseEntity<Map<String, String>> createOrUpdateCart(@RequestBody Cart cart) {
        if (cart.getCartId() == null || cart.getCartId().isBlank()) {
            String id = "cart-" + cartIdCounter.getAndIncrement();
            cart.setCartId(id);
        }
        cart.setStatus(cart.getStatus() == null || cart.getStatus().isBlank() ? "ACTIVE" : cart.getStatus());
        cart.setCreatedAt(java.time.LocalDateTime.now());

        if (!cart.isValid()) {
            log.error("Invalid Cart entity: {}", cart);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        cartCache.put(cart.getCartId(), cart);
        log.info("Cart created/updated with id {}", cart.getCartId());
        processCart(cart.getCartId(), cart);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", cart.getCartId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    // Processing methods with meaningful business logic

    private void processUserImportJob(String technicalId, UserImportJob entity) {
        log.info("Processing UserImportJob with id {}", technicalId);

        // Validate importData format
        if (entity.getImportData() == null || entity.getImportData().isBlank()) {
            log.error("UserImportJob importData is blank");
            entity.setStatus("FAILED");
            return;
        }
        entity.setStatus("PROCESSING");

        // Parse importData (assuming JSON array of users)
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, String>> usersData = mapper.readValue(entity.getImportData(), List.class);

            for (Map<String, String> userData : usersData) {
                User user = new User();
                user.setUserId("user-" + userIdCounter.getAndIncrement());
                user.setName(userData.getOrDefault("name", ""));
                user.setEmail(userData.getOrDefault("email", ""));
                user.setRole(userData.getOrDefault("role", ""));
                user.setCreatedAt(java.time.LocalDateTime.now());

                if (user.isValid()) {
                    userCache.put(user.getUserId(), user);
                    processUser(user.getUserId(), user);
                    log.info("User imported: {}", user.getUserId());
                } else {
                    log.error("Invalid user data in import: {}", userData);
                }
            }
            entity.setStatus("COMPLETED");
        } catch (Exception e) {
            log.error("Failed to parse importData", e);
            entity.setStatus("FAILED");
        }
    }

    private void processUser(String technicalId, User entity) {
        log.info("Processing User with id {}", technicalId);
        // Example business logic: could add notification or role validation
        if (!entity.isValid()) {
            log.error("User validation failed for id {}", technicalId);
        } else {
            log.info("User validated successfully for id {}", technicalId);
        }
    }

    private void processCart(String technicalId, Cart entity) {
        log.info("Processing Cart with id {}", technicalId);

        // Validate product availability for each CartItem (simulated)
        if (entity.getItems() != null) {
            boolean allAvailable = true;
            for (var item : entity.getItems()) {
                // Simulate stock check
                if (item.getQuantity() <= 0) {
                    log.error("Invalid quantity for product {} in cart {}", item.getProductId(), technicalId);
                    allAvailable = false;
                }
            }
            if (!allAvailable) {
                log.error("Cart processing failed due to invalid items quantities");
                return;
            }
        }
        entity.setStatus("ACTIVE");
        log.info("Cart processed successfully with id {}", technicalId);
    }
}