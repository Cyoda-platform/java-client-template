package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.address.version_1.Address;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
@RequestMapping("/entity/Address")
@Tag(name = "Address")
public class AddressController {
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(AddressController.class);

    public AddressController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Address", description = "Create a new Address. requestId required for idempotency.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request")
    })
    @PostMapping
    public ResponseEntity<?> createAddress(@RequestBody CreateAddressRequest request) {
        try {
            if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
                throw new IllegalArgumentException("requestId is required for idempotency");
            }
            ObjectNode data = objectMapper.convertValue(request, ObjectNode.class);
            UUID id = entityService.addItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    data
            ).get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid create address request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception e) {
            logger.error("Error creating address", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Address", description = "Retrieve Address by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Address.class))),
            @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getAddress(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            ObjectNode node = entityService.getItem(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            ).get();
            return ResponseEntity.ok(node);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            logger.error("Execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching address", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Addresses", description = "List all Addresses")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Address.class))))
    })
    @GetMapping
    public ResponseEntity<?> listAddresses() {
        try {
            ArrayNode arr = entityService.getItems(
                    Address.ENTITY_NAME,
                    String.valueOf(Address.ENTITY_VERSION)
            ).get();
            return ResponseEntity.ok(arr);
        } catch (Exception e) {
            logger.error("Error listing addresses", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class CreateAddressRequest {
        @Schema(description = "Idempotency request id", required = true)
        private String requestId;
        @Schema(description = "User technical id")
        private String userId;
        @Schema(description = "Address line 1")
        private String line1;
        @Schema(description = "City")
        private String city;
        @Schema(description = "Postcode")
        private String postcode;
        @Schema(description = "Country")
        private String country;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id of the created resource")
        private String technicalId;
    }
}
