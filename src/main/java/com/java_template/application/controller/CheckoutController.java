package com.java_template.application.controller;

import com.java_template.application.entity.cart.version_1.Cart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CheckoutController - Checkout API
 * Endpoint: POST /ui/checkout/{cartId}
 */
@RestController
@RequestMapping("/ui/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CheckoutController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Checkout with guest contact
     * POST /ui/checkout/{cartId}
     */
    @PostMapping("/{cartId}")
    public ResponseEntity<EntityWithMetadata<Cart>> checkout(
            @PathVariable String cartId,
            @RequestBody CheckoutRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Cart.ENTITY_NAME).withVersion(Cart.ENTITY_VERSION);
            
            SimpleCondition cartIdCondition = new SimpleCondition()
                    .withJsonPath("$.cartId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(cartId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(cartIdCondition));

            List<EntityWithMetadata<Cart>> results = entityService.search(
                    modelSpec, condition, Cart.class,
                    com.java_template.common.repository.SearchAndRetrievalParams.builder()
                            .pageSize(1)
                            .inMemory(true)
                            .build()).data();

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Cart> cartWithMetadata = results.get(0);
            Cart cart = cartWithMetadata.entity();

            // Attach guest contact
            if (request.getGuestContact() != null) {
                Cart.GuestContact guestContact = new Cart.GuestContact();
                guestContact.setName(request.getGuestContact().getName());
                guestContact.setEmail(request.getGuestContact().getEmail());
                guestContact.setPhone(request.getGuestContact().getPhone());
                
                if (request.getGuestContact().getAddress() != null) {
                    Cart.Address address = new Cart.Address();
                    address.setLine1(request.getGuestContact().getAddress().getLine1());
                    address.setCity(request.getGuestContact().getAddress().getCity());
                    address.setPostcode(request.getGuestContact().getAddress().getPostcode());
                    address.setCountry(request.getGuestContact().getAddress().getCountry());
                    guestContact.setAddress(address);
                }
                
                cart.setGuestContact(guestContact);
            }

            cart.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<Cart> response = entityService.update(cartWithMetadata.metadata().getId(), cart, null);
            
            logger.info("Checkout completed for cart: {}", cartId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    String.format("Failed to checkout: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @Getter
    @Setter
    public static class CheckoutRequest {
        private GuestContactRequest guestContact;
    }

    @Getter
    @Setter
    public static class GuestContactRequest {
        private String name;
        private String email;
        private String phone;
        private AddressRequest address;
    }

    @Getter
    @Setter
    public static class AddressRequest {
        private String line1;
        private String city;
        private String postcode;
        private String country;
    }
}

