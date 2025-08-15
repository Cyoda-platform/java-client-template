package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.lead.version_1.Lead;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/leads")
@Tag(name = "Lead API", description = "APIs to manage Leads (proxy to entity service).")
public class LeadController {

    private static final Logger logger = LoggerFactory.getLogger(LeadController.class);

    private final EntityService entityService;

    public LeadController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Lead", description = "Creates a Lead entity and triggers Lead workflow. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Accepted", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createLead(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Lead payload",
            content = @Content(schema = @Schema(implementation = LeadRequest.class))
    ) @RequestBody LeadRequest request) {
        try {
            Lead lead = new Lead();
            lead.setFirstName(request.getFirstName());
            lead.setLastName(request.getLastName());
            lead.setEmail(request.getEmail());
            lead.setPhone(request.getPhone());
            lead.setCompany(request.getCompany());
            lead.setSource(request.getSource());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Lead.ENTITY_NAME,
                    String.valueOf(Lead.ENTITY_VERSION),
                    lead
            );

            UUID id = idFuture.get();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for creating lead", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("Execution exception when creating lead", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating lead", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating lead", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Lead", description = "Retrieves a stored Lead by technicalId including workflow state.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getLead(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Lead.ENTITY_NAME,
                    String.valueOf(Lead.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for getLead", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("Execution exception when fetching lead", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when fetching lead", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when fetching lead", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Leads", description = "Retrieves all stored Leads (read-only).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class))))
    })
    @GetMapping
    public ResponseEntity<?> listLeads() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Lead.ENTITY_NAME,
                    String.valueOf(Lead.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("Execution exception when listing leads", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when listing leads", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when listing leads", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "LeadRequest", description = "Payload to create a Lead")
    public static class LeadRequest {
        @Schema(description = "Lead first name", example = "John")
        private String firstName;

        @Schema(description = "Lead last name", example = "Doe")
        private String lastName;

        @Schema(description = "Lead email", example = "lead@example.com")
        private String email;

        @Schema(description = "Lead phone", example = "555-111-2222")
        private String phone;

        @Schema(description = "Source of the lead", example = "web_form")
        private String source;

        @Schema(description = "Company name", example = "Acme Inc")
        private String company;

        @Schema(description = "Potential value of the lead", example = "12000")
        private Double potentialValue;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Simple response containing the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id generated by the datastore", example = "lead-0001")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
