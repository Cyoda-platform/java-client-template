package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/mails")
@Tag(name = "Mail Controller", description = "Endpoints for Mail entity")
public class MailController {

    private static final Logger logger = LoggerFactory.getLogger(MailController.class);
    private final EntityService entityService;

    public MailController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Mail", description = "Persist a new Mail and trigger the processing workflow. Any provided isHappy in the request is ignored.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateMailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CreateMailResponse> createMail(@Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Mail create request") @RequestBody CreateMailRequest request) {
        try {
            // Map request to entity. Do not honor client-provided isHappy; platform evaluates it.
            Mail mail = new Mail();
            mail.setMailList(request.getMailList());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    mail
            );

            UUID technicalId = idFuture.get();

            CreateMailResponse response = new CreateMailResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request to create mail", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution error while creating mail", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while creating mail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while creating mail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Mail", description = "Retrieve a Mail by its technicalId including workflow state and delivery metadata.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = MailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getMail(
            @Parameter(name = "technicalId", description = "Technical ID of the Mail") @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    String.valueOf(Mail.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId provided", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution error while retrieving mail", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving mail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving mail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateMailRequest", description = "Request payload to create a Mail. Any provided isHappy will be ignored by the server.")
    public static class CreateMailRequest {
        @Schema(description = "List of recipient email addresses", required = true)
        @NotNull
        @Size(min = 1)
        private List<@Email String> mailList;

        @Schema(description = "Ignored by server; evaluated by the platform", accessMode = Schema.AccessMode.WRITE_ONLY)
        private Boolean isHappy;
    }

    @Data
    @Schema(name = "CreateMailResponse", description = "Response returned after creating a Mail")
    public static class CreateMailResponse {
        @Schema(description = "Technical ID of the created mail", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeliveryStatus", description = "Delivery metadata for a Mail")
    public static class DeliveryStatusDto {
        @Schema(description = "Number of send attempts")
        private Integer attempts;

        @Schema(description = "ISO-8601 timestamp of last attempt")
        private String lastAttempt;

        @Schema(description = "Delivery status (PENDING, SENDING, SENT, FAILED)")
        private String status;

        @Schema(description = "Last error message or code from failure")
        private String lastError;
    }

    @Data
    @Schema(name = "MailResponse", description = "Representation of stored Mail including workflow metadata")
    public static class MailResponse {
        @Schema(description = "Technical ID of the mail")
        private String technicalId;

        @Schema(description = "List of recipient email addresses")
        private List<String> mailList;

        @Schema(description = "Classification computed by platform (true=happy, false=gloomy)")
        private Boolean isHappy;

        @Schema(description = "Workflow state")
        private String state;

        @Schema(description = "Delivery status metadata")
        private DeliveryStatusDto deliveryStatus;

        @Schema(description = "Creation timestamp (ISO-8601)")
        private String createdAt;

        @Schema(description = "Last update timestamp (ISO-8601)")
        private String updatedAt;
    }
}
