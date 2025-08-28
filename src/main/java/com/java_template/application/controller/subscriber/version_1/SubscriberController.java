package com.java_template.application.controller.subscriber.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/subscribers")
@Tag(name = "Subscriber Controller", description = "APIs for Subscriber entity (version 1)")
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SubscriberController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Register Subscriber", description = "Register a new subscriber. Returns the technicalId of the created entity.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateSubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createSubscriber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Subscriber registration payload", required = true,
                    content = @Content(schema = @Schema(implementation = CreateSubscriberRequest.class)))
            @RequestBody CreateSubscriberRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Basic format validation
            if (request.getSubscriberId() == null || request.getSubscriberId().isBlank()) {
                throw new IllegalArgumentException("subscriberId is required");
            }
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getActive() == null) {
                throw new IllegalArgumentException("active flag is required");
            }
            if (request.getChannels() == null || request.getChannels().isEmpty()) {
                throw new IllegalArgumentException("channels are required");
            }

            // Map request to entity
            Subscriber entity = new Subscriber();
            entity.setSubscriberId(request.getSubscriberId());
            entity.setName(request.getName());
            entity.setActive(request.getActive());

            List<Subscriber.Channel> channels = new ArrayList<>();
            if (request.getChannels() != null) {
                for (CreateSubscriberRequest.ChannelDTO c : request.getChannels()) {
                    Subscriber.Channel channel = new Subscriber.Channel();
                    channel.setType(c.getType());
                    channel.setAddress(c.getAddress());
                    channels.add(channel);
                }
            }
            entity.setChannels(channels);

            List<Subscriber.Filter> filters = null;
            if (request.getFilters() != null) {
                filters = new ArrayList<>();
                for (CreateSubscriberRequest.FilterDTO f : request.getFilters()) {
                    Subscriber.Filter filter = new Subscriber.Filter();
                    filter.setCategory(f.getCategory());
                    filters.add(filter);
                }
            }
            entity.setFilters(filters);

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Subscriber.ENTITY_NAME,
                    Subscriber.ENTITY_VERSION,
                    entity
            );
            UUID createdId = idFuture.get();
            CreateSubscriberResponse resp = new CreateSubscriberResponse();
            resp.setTechnicalId(createdId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createSubscriber: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createSubscriber", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception ex) {
            logger.error("Exception in createSubscriber", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Get Subscriber by technicalId", description = "Retrieve a subscriber by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SubscriberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSubscriberById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.status(404).body("Subscriber not found");
            }
            // Convert payload to entity
            Subscriber entity = objectMapper.treeToValue((JsonNode) node, Subscriber.class);
            SubscriberResponse resp = new SubscriberResponse();
            resp.setTechnicalId(technicalId);
            resp.setSubscriberId(entity.getSubscriberId());
            resp.setName(entity.getName());
            resp.setActive(entity.getActive());
            resp.setLastNotifiedAt(entity.getLastNotifiedAt());

            // map channels
            List<SubscriberResponse.ChannelDTO> channelDTOs = new ArrayList<>();
            if (entity.getChannels() != null) {
                for (Subscriber.Channel c : entity.getChannels()) {
                    SubscriberResponse.ChannelDTO cd = new SubscriberResponse.ChannelDTO();
                    cd.setType(c.getType());
                    cd.setAddress(c.getAddress());
                    channelDTOs.add(cd);
                }
            }
            resp.setChannels(channelDTOs);

            // map filters
            List<SubscriberResponse.FilterDTO> filterDTOs = null;
            if (entity.getFilters() != null) {
                filterDTOs = new ArrayList<>();
                for (Subscriber.Filter f : entity.getFilters()) {
                    SubscriberResponse.FilterDTO fd = new SubscriberResponse.FilterDTO();
                    fd.setCategory(f.getCategory());
                    filterDTOs.add(fd);
                }
            }
            resp.setFilters(filterDTOs);

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getSubscriberById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getSubscriberById", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception ex) {
            logger.error("Exception in getSubscriberById", ex);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Request/Response DTOs

    @Data
    @Schema(name = "CreateSubscriberRequest", description = "Request payload to create a Subscriber")
    public static class CreateSubscriberRequest {
        @Schema(description = "Subscriber identifier", required = true, example = "sub-abc")
        private String subscriberId;

        @Schema(description = "Display name", required = true, example = "Nobel Alerts")
        private String name;

        @Schema(description = "Channels list", required = true)
        private List<ChannelDTO> channels;

        @Schema(description = "Active flag", required = true)
        private Boolean active;

        @Schema(description = "Optional filters")
        private List<FilterDTO> filters;

        @Data
        @Schema(name = "ChannelDTO", description = "Channel information")
        public static class ChannelDTO {
            @Schema(description = "Channel type", example = "email", required = true)
            private String type;
            @Schema(description = "Channel address", example = "alerts@example.com", required = true)
            private String address;
        }

        @Data
        @Schema(name = "FilterDTO", description = "Filter information")
        public static class FilterDTO {
            @Schema(description = "Category filter", example = "Chemistry")
            private String category;
        }
    }

    @Data
    @Schema(name = "CreateSubscriberResponse", description = "Response after creating a Subscriber")
    public static class CreateSubscriberResponse {
        @Schema(description = "Technical id of the created subscriber", example = "subscriber-technical-007")
        private String technicalId;
    }

    @Data
    @Schema(name = "SubscriberResponse", description = "Subscriber response payload")
    public static class SubscriberResponse {
        @Schema(description = "Technical id", example = "subscriber-technical-007")
        private String technicalId;

        @Schema(description = "Subscriber identifier", example = "sub-abc")
        private String subscriberId;

        @Schema(description = "Display name", example = "Nobel Alerts")
        private String name;

        @Schema(description = "Channels list")
        private List<ChannelDTO> channels;

        @Schema(description = "Active flag")
        private Boolean active;

        @Schema(description = "Optional filters")
        private List<FilterDTO> filters;

        @Schema(description = "Last notified timestamp (ISO-8601) or null")
        private String lastNotifiedAt;

        @Data
        @Schema(name = "ChannelDTO", description = "Channel information")
        public static class ChannelDTO {
            @Schema(description = "Channel type", example = "email")
            private String type;
            @Schema(description = "Channel address", example = "alerts@example.com")
            private String address;
        }

        @Data
        @Schema(name = "FilterDTO", description = "Filter information")
        public static class FilterDTO {
            @Schema(description = "Category filter", example = "Chemistry")
            private String category;
        }
    }
}