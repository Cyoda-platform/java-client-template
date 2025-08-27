package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/subscriber/v1")
@Tag(name = "Subscriber", description = "Subscriber entity API (version 1) - proxy to EntityService")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SubscriberController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Subscriber", description = "Persist a Subscriber entity. Returns technicalId string.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber create payload", required = true,
                    content = @Content(schema = @Schema(implementation = SubscriberRequest.class)))
            @RequestBody SubscriberRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Subscriber entity = mapToEntity(request);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    entity
            );
            UUID technicalId = idFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating subscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Subscribers", description = "Persist multiple Subscriber entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createSubscribersBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of subscribers to create", required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberRequest.class))))
            @RequestBody List<SubscriberRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request body must contain at least one subscriber");

            List<Subscriber> entities = new ArrayList<>();
            for (SubscriberRequest r : requests) {
                entities.add(mapToEntity(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<IdResponse> resp = new ArrayList<>();
            for (UUID id : ids) {
                IdResponse ir = new IdResponse();
                ir.setTechnicalId(id.toString());
                resp.add(ir);
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid bulk request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating subscribers bulk", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while creating subscribers bulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a Subscriber by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Subscriber not found");
            }
            SubscriberResponse resp = objectMapper.treeToValue(node, SubscriberResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid get request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving subscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Subscribers", description = "Retrieve all Subscriber entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllSubscribers() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<SubscriberResponse> resp = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    resp.add(objectMapper.treeToValue(payload.getData(), SubscriberResponse.class));
                }
            }
            return ResponseEntity.ok(resp);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while retrieving all subscribers", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving all subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Subscribers by condition", description = "Retrieve Subscribers by a simple search condition (in-memory filtering)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = SubscriberResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchSubscribers(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition request", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    condition,
                    true
            );
            List<DataPayload> dataPayloads = filteredItemsFuture.get();
            List<SubscriberResponse> resp = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    resp.add(objectMapper.treeToValue(payload.getData(), SubscriberResponse.class));
                }
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while searching subscribers", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while searching subscribers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update Subscriber", description = "Update a Subscriber entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber update payload", required = true,
                    content = @Content(schema = @Schema(implementation = SubscriberRequest.class)))
            @RequestBody SubscriberRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");

            Subscriber entity = mapToEntity(request);

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    UUID.fromString(technicalId),
                    entity
            );
            UUID updatedId = updatedIdFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid update request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while updating subscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while updating subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete Subscriber", description = "Delete a Subscriber entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteSubscriber(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedIdFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid delete request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while deleting subscriber", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error while deleting subscriber", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper mapper - only maps fields between DTO and entity (no business logic)
    private Subscriber mapToEntity(SubscriberRequest req) {
        Subscriber s = new Subscriber();
        s.setSubscriberId(req.getSubscriberId());
        s.setName(req.getName());
        s.setActive(req.getActive());
        s.setLastNotificationStatus(req.getLastNotificationStatus());
        s.setLastNotifiedJobId(req.getLastNotifiedJobId());
        s.setPreferredPayload(req.getPreferredPayload());

        if (req.getContactMethods() != null) {
            Subscriber.ContactMethods cm = new Subscriber.ContactMethods();
            cm.setEmail(req.getContactMethods().getEmail());
            cm.setWebhookUrl(req.getContactMethods().getWebhookUrl());
            s.setContactMethods(cm);
        }

        if (req.getInterests() != null) {
            Subscriber.Interests in = new Subscriber.Interests();
            in.setCategories(req.getInterests().getCategories());
            in.setCountries(req.getInterests().getCountries());
            in.setYears(req.getInterests().getYears());
            s.setInterests(in);
        }

        return s;
    }

    // Static DTOs for requests/responses

    @Schema(name = "SubscriberRequest", description = "Payload to create/update a Subscriber")
    public static class SubscriberRequest {
        @Schema(description = "Business subscriber id", example = "sub-1")
        private String subscriberId;
        @Schema(description = "Display name", example = "Nobel Alerts")
        private String name;
        @Schema(description = "Active flag")
        private Boolean active;
        @Schema(description = "Contact methods")
        private ContactMethodsDto contactMethods;
        @Schema(description = "Interests")
        private InterestsDto interests;
        @Schema(description = "Preferred payload", example = "summary")
        private String preferredPayload;
        @Schema(description = "Last notified job id (string/UUID)", example = "job-2025-08-27-01")
        private String lastNotifiedJobId;
        @Schema(description = "Last notification status", example = "SUCCEEDED")
        private String lastNotificationStatus;

        public String getSubscriberId() {
            return subscriberId;
        }

        public void setSubscriberId(String subscriberId) {
            this.subscriberId = subscriberId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public ContactMethodsDto getContactMethods() {
            return contactMethods;
        }

        public void setContactMethods(ContactMethodsDto contactMethods) {
            this.contactMethods = contactMethods;
        }

        public InterestsDto getInterests() {
            return interests;
        }

        public void setInterests(InterestsDto interests) {
            this.interests = interests;
        }

        public String getPreferredPayload() {
            return preferredPayload;
        }

        public void setPreferredPayload(String preferredPayload) {
            this.preferredPayload = preferredPayload;
        }

        public String getLastNotifiedJobId() {
            return lastNotifiedJobId;
        }

        public void setLastNotifiedJobId(String lastNotifiedJobId) {
            this.lastNotifiedJobId = lastNotifiedJobId;
        }

        public String getLastNotificationStatus() {
            return lastNotificationStatus;
        }

        public void setLastNotificationStatus(String lastNotificationStatus) {
            this.lastNotificationStatus = lastNotificationStatus;
        }

        @Schema(name = "ContactMethods", description = "Contact methods for subscriber")
        public static class ContactMethodsDto {
            @Schema(description = "Email address", example = "alerts@example.com")
            private String email;
            @Schema(description = "Webhook URL", example = "https://example.com/webhook")
            private String webhookUrl;

            public String getEmail() {
                return email;
            }

            public void setEmail(String email) {
                this.email = email;
            }

            public String getWebhookUrl() {
                return webhookUrl;
            }

            public void setWebhookUrl(String webhookUrl) {
                this.webhookUrl = webhookUrl;
            }
        }

        @Schema(name = "Interests", description = "Subscriber interests")
        public static class InterestsDto {
            @Schema(description = "Categories of interest", example = "[\"physics\",\"chemistry\"]")
            private List<String> categories;
            @Schema(description = "Countries of interest", example = "[\"SE\",\"US\"]")
            private List<String> countries;
            @Schema(description = "Years of interest", example = "[\"2020\",\"2021\"]")
            private List<String> years;

            public List<String> getCategories() {
                return categories;
            }

            public void setCategories(List<String> categories) {
                this.categories = categories;
            }

            public List<String> getCountries() {
                return countries;
            }

            public void setCountries(List<String> countries) {
                this.countries = countries;
            }

            public List<String> getYears() {
                return years;
            }

            public void setYears(List<String> years) {
                this.years = years;
            }
        }
    }

    @Schema(name = "SubscriberResponse", description = "Subscriber response payload")
    public static class SubscriberResponse {
        @Schema(description = "Business subscriber id", example = "sub-1")
        private String subscriberId;
        @Schema(description = "Display name", example = "Nobel Alerts")
        private String name;
        @Schema(description = "Active flag")
        private Boolean active;
        @Schema(description = "Contact methods")
        private ContactMethodsDto contactMethods;
        @Schema(description = "Interests")
        private InterestsDto interests;
        @Schema(description = "Preferred payload", example = "summary")
        private String preferredPayload;
        @Schema(description = "Last notified job id (string/UUID)", example = "job-2025-08-27-01")
        private String lastNotifiedJobId;
        @Schema(description = "Last notification status", example = "SUCCEEDED")
        private String lastNotificationStatus;

        public String getSubscriberId() {
            return subscriberId;
        }

        public void setSubscriberId(String subscriberId) {
            this.subscriberId = subscriberId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public ContactMethodsDto getContactMethods() {
            return contactMethods;
        }

        public void setContactMethods(ContactMethodsDto contactMethods) {
            this.contactMethods = contactMethods;
        }

        public InterestsDto getInterests() {
            return interests;
        }

        public void setInterests(InterestsDto interests) {
            this.interests = interests;
        }

        public String getPreferredPayload() {
            return preferredPayload;
        }

        public void setPreferredPayload(String preferredPayload) {
            this.preferredPayload = preferredPayload;
        }

        public String getLastNotifiedJobId() {
            return lastNotifiedJobId;
        }

        public void setLastNotifiedJobId(String lastNotifiedJobId) {
            this.lastNotifiedJobId = lastNotifiedJobId;
        }

        public String getLastNotificationStatus() {
            return lastNotificationStatus;
        }

        public void setLastNotificationStatus(String lastNotificationStatus) {
            this.lastNotificationStatus = lastNotificationStatus;
        }

        public static class ContactMethodsDto {
            private String email;
            private String webhookUrl;

            public String getEmail() {
                return email;
            }

            public void setEmail(String email) {
                this.email = email;
            }

            public String getWebhookUrl() {
                return webhookUrl;
            }

            public void setWebhookUrl(String webhookUrl) {
                this.webhookUrl = webhookUrl;
            }
        }

        public static class InterestsDto {
            private List<String> categories;
            private List<String> countries;
            private List<String> years;

            public List<String> getCategories() {
                return categories;
            }

            public void setCategories(List<String> categories) {
                this.categories = categories;
            }

            public List<String> getCountries() {
                return countries;
            }

            public void setCountries(List<String> countries) {
                this.countries = countries;
            }

            public List<String> getYears() {
                return years;
            }

            public void setYears(List<String> years) {
                this.years = years;
            }
        }
    }

    @Schema(name = "IdResponse", description = "Response containing technicalId")
    public static class IdResponse {
        @Schema(description = "Technical id returned by the system", example = "5f8d0d3a-2f2b-4a12-9f9a-1a2b3c4d5e6f")
        private String technicalId;

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}