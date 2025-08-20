package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.order.version_1.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/checkout")
@Tag(name = "Checkout")
public class CheckoutController {
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(CheckoutController.class);

    public CheckoutController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Identify during checkout", description = "Identify user and create address. requestId required for idempotency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request")
    })
    @PostMapping("/identify")
    public ResponseEntity<?> identify(@RequestBody IdentifyRequest request) {
        try {
            if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
                throw new IllegalArgumentException("requestId is required for idempotency");
            }
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            // Create a Job to trigger IdentifyProcessor / UpsertUserProcessor workflows
            UUID id = entityService.addItem(
                    Job.ENTITY_NAME,
                    String.valueOf(Job.ENTITY_VERSION),
                    data
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid identify request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception e) {
            logger.error("Error during identify", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Place Order (checkout)", description = "Place an order from a cart. requestId required for idempotency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "409", description = "Conflict")
    })
    @PostMapping("/place-order")
    public ResponseEntity<?> placeOrder(@RequestBody PlaceOrderRequest request) {
        try {
            if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
                throw new IllegalArgumentException("requestId is required for idempotency");
            }
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            // Proxy to create Order entity; PlaceOrderProcessor workflows will validate stock and reserve
            UUID id = entityService.addItem(
                    Order.ENTITY_NAME,
                    String.valueOf(Order.ENTITY_VERSION),
                    data
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid place order request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception e) {
            logger.error("Error placing order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class IdentifyRequest {
        @Schema(description = "Idempotency request id", required = true)
        private String requestId;
        @Schema(description = "Cart technical id")
        private String cartTechnicalId;
        @Schema(description = "Name")
        private String name;
        @Schema(description = "Email")
        private String email;
        @Schema(description = "Phone")
        private String phone;
        @Schema(description = "Address object")
        private AddressDto address;
    }

    @Data
    static class AddressDto {
        @Schema(description = "Line 1")
        private String line1;
        @Schema(description = "City")
        private String city;
        @Schema(description = "Postcode")
        private String postcode;
        @Schema(description = "Country")
        private String country;
    }

    @Data
    static class PlaceOrderRequest {
        @Schema(description = "Idempotency request id", required = true)
        private String requestId;
        @Schema(description = "Cart technical id")
        private String cartTechnicalId;
        @Schema(description = "User technical id")
        private String userTechnicalId;
        @Schema(description = "Address technical id")
        private String addressTechnicalId;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id of the created resource")
        private String technicalId;
    }
}
