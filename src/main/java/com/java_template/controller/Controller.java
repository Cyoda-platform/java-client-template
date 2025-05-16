package com.java_template.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-messages")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Message createMessage(@RequestBody @Valid CreateMessageRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received create message request: {}", request.getText());

        // Create entity ObjectNode for persistence
        ObjectNode entity = entityService.getObjectMapper().createObjectNode();
        entity.put("text", request.getText());

        // Add entity without workflow function
        UUID technicalId = entityService.addItem(
                "message",
                ENTITY_VERSION,
                entity
        ).get();

        // Prepare response DTO with persisted data
        Message message = new Message();
        message.setId(technicalId.toString());
        message.setText(request.getText());
        // timestamp was set in workflow function, retrieve safely
        if (entity.hasNonNull("timestamp")) {
            message.setTimestamp(entity.get("timestamp").asText());
        } else {
            message.setTimestamp(null);
        }

        logger.info("Message stored with technicalId: {}", technicalId);
        return message;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Message getMessageById(@PathVariable String id) throws ExecutionException, InterruptedException {
        logger.info("Fetching message by id: {}", id);

        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid id format");
        }

        ObjectNode entityNode = entityService.getItem("message", ENTITY_VERSION, uuid).get();
        if (entityNode == null) {
            logger.error("Message not found for id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Message not found");
        }

        Message message = new Message();
        if (entityNode.hasNonNull("technicalId")) {
            message.setId(entityNode.get("technicalId").asText());
        } else {
            message.setId(id); // fallback to requested id
        }
        message.setText(entityNode.hasNonNull("text") ? entityNode.get("text").asText() : null);
        message.setTimestamp(entityNode.hasNonNull("timestamp") ? entityNode.get("timestamp").asText() : null);

        return message;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Message> getAllMessages() throws ExecutionException, InterruptedException {
        logger.info("Fetching all messages");

        ArrayNode entities = entityService.getItems("message", ENTITY_VERSION).get();

        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        return entities.findValues("technicalId").stream()
                .map(node -> {
                    try {
                        if (node == null || node.getParent() == null) {
                            return null;
                        }
                        ObjectNode obj = (ObjectNode) node.getParent();
                        Message m = new Message();
                        m.setId(obj.hasNonNull("technicalId") ? obj.get("technicalId").asText() : null);
                        m.setText(obj.hasNonNull("text") ? obj.get("text").asText() : null);
                        m.setTimestamp(obj.hasNonNull("timestamp") ? obj.get("timestamp").asText() : null);
                        return m;
                    } catch (Exception e) {
                        logger.error("Error parsing message entity", e);
                        return null;
                    }
                })
                .filter(m -> m != null)
                .toList();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String id;
        private String text;
        private String timestamp;
    }

    @Data
    public static class CreateMessageRequest {
        @NotBlank(message = "text must not be blank")
        @Size(max = 500, message = "text must be at most 500 characters")
        private String text;
    }
}