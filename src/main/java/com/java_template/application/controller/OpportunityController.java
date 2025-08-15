package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.opportunity.version_1.Opportunity;
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
@RequestMapping("/opportunities")
@Tag(name = "Opportunity API", description = "APIs to manage Opportunities (proxy to entity service).")
public class OpportunityController {

    private static final Logger logger = LoggerFactory.getLogger(OpportunityController.class);

    private final EntityService entityService;

    public OpportunityController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Opportunity", description = "Creates an Opportunity entity and triggers Opportunity workflow. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Accepted", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createOpportunity(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Opportunity payload",
            content = @Content(schema = @Schema(implementation = OpportunityRequest.class))
    ) @RequestBody OpportunityRequest request) {
        try {
            Opportunity opp = new Opportunity();
            opp.setName(request.getTitle());
            opp.setContactId(request.getContactId());
            opp.setLeadId(request.getLeadId());
            opp.setAmount(request.getAmount());
            opp.setStage(request.getStage());
            opp.setCloseDate(request.getExpectedCloseDate());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Opportunity.ENTITY_NAME,
                    String.valueOf(Opportunity.ENTITY_VERSION),
                    opp
            );

            UUID id = idFuture.get();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for creating opportunity", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("Execution exception when creating opportunity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating opportunity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating opportunity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Opportunity", description = "Retrieves a stored Opportunity by technicalId including workflow state.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getOpportunity(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Opportunity.ENTITY_NAME,
                    String.valueOf(Opportunity.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for getOpportunity", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("Execution exception when fetching opportunity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when fetching opportunity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when fetching opportunity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Opportunities", description = "Retrieves all stored Opportunities (read-only).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class))))
    })
    @GetMapping
    public ResponseEntity<?> listOpportunities() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Opportunity.ENTITY_NAME,
                    String.valueOf(Opportunity.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("Execution exception when listing opportunities", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when listing opportunities", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when listing opportunities", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "OpportunityRequest", description = "Payload to create an Opportunity")
    public static class OpportunityRequest {
        @Schema(description = "Opportunity title", example = "Q4 Big Deal")
        private String title;

        @Schema(description = "Reference contact technicalId", example = "contact-0001")
        private String contactId;

        @Schema(description = "Reference lead technicalId", example = "lead-0001")
        private String leadId;

        @Schema(description = "Monetary amount", example = "50000")
        private Double amount;

        @Schema(description = "Opportunity stage", example = "PROSPECTING")
        private String stage;

        @Schema(description = "Close probability (0-100)", example = "10")
        private Integer closeProbability;

        @Schema(description = "Expected close date (ISO)", example = "2025-12-31")
        private String expectedCloseDate;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Simple response containing the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id generated by the datastore", example = "opp-0001")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
