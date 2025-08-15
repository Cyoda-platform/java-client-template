package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.contact.version_1.Contact;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
@RequestMapping("/contacts")
@Tag(name = "Contact API", description = "APIs to manage Contacts (proxy to entity service).")
public class ContactController {

    private static final Logger logger = LoggerFactory.getLogger(ContactController.class);

    private final EntityService entityService;

    public ContactController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Contact", description = "Creates a Contact entity and triggers Contact workflow. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Accepted", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createContact(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Contact payload",
            content = @Content(schema = @Schema(implementation = ContactRequest.class))
    ) @RequestBody ContactRequest request) {
        try {
            // Map request DTO to entity model (reuse existing entity class)
            Contact contact = new Contact();
            contact.setFirstName(request.getFirstName());
            contact.setLastName(request.getLastName());
            contact.setEmail(request.getEmail());
            contact.setPhone(request.getPhone());
            contact.setCompany(request.getCompany());
            // Note: Contact entity does not have status/source in its model; those fields are part of request only

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Contact.ENTITY_NAME,
                    String.valueOf(Contact.ENTITY_VERSION),
                    contact
            );

            UUID id = idFuture.get();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for creating contact", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("Execution exception when creating contact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating contact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when creating contact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Contact", description = "Retrieves a stored Contact by technicalId including workflow state.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getContact(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Contact.ENTITY_NAME,
                    String.valueOf(Contact.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId for getContact", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            }
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("Execution exception when fetching contact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when fetching contact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when fetching contact", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List Contacts", description = "Retrieves all stored Contacts (read-only).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class))))
    })
    @GetMapping
    public ResponseEntity<?> listContacts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Contact.ENTITY_NAME,
                    String.valueOf(Contact.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            }
            logger.error("Execution exception when listing contacts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when listing contacts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error when listing contacts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    @Schema(name = "ContactRequest", description = "Payload to create a Contact")
    public static class ContactRequest {
        @Schema(description = "Contact first name", example = "John")
        private String firstName;

        @Schema(description = "Contact last name", example = "Doe")
        private String lastName;

        @Schema(description = "Contact email", example = "john.doe@example.com")
        private String email;

        @Schema(description = "Contact phone", example = "123-456-7890")
        private String phone;

        @Schema(description = "Company name", example = "Acme Inc")
        private String company;

        @Schema(description = "Business status like NEW, VERIFIED, ARCHIVED", example = "NEW")
        private String status;

        @Schema(description = "Source of contact", example = "crm_api")
        private String source;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Simple response containing the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id generated by the datastore", example = "contact-0001")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
