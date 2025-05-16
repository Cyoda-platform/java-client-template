package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-messages")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Message createMessage(@RequestBody @Valid CreateMessageRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received create message request: {}", request.getText());

        Message message = new Message();
        message.setText(request.getText());
        message.setTimestamp(Instant.now().toString());

        UUID technicalId = entityService.addItem("message", ENTITY_VERSION, message).get();

        message.setId(technicalId.toString());

        logger.info("Message stored with technicalId: {}", technicalId);
        return message;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Message getMessageById(@PathVariable String id) throws ExecutionException, InterruptedException {
        logger.info("Fetching message by id: {}", id);

        ObjectNode entityNode = entityService.getItem("message", ENTITY_VERSION, UUID.fromString(id)).get();
        if (entityNode == null) {
            logger.error("Message not found for id: {}", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Message not found");
        }

        Message message = new Message();
        message.setId(entityNode.get("technicalId").asText());
        message.setText(entityNode.get("text").asText());
        message.setTimestamp(entityNode.get("timestamp").asText());

        return message;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Message> getAllMessages() throws ExecutionException, InterruptedException {
        logger.info("Fetching all messages");

        ArrayNode entities = entityService.getItems("message", ENTITY_VERSION).get();

        return entities.findValuesAsText("technicalId").isEmpty() ? List.of() :
                entities.findValues("technicalId").stream().map(node -> {
                    try {
                        ObjectNode obj = (ObjectNode) node.getParent();
                        Message m = new Message();
                        m.setId(obj.get("technicalId").asText());
                        m.setText(obj.get("text").asText());
                        m.setTimestamp(obj.get("timestamp").asText());
                        return m;
                    } catch (Exception e) {
                        logger.error("Error parsing message entity", e);
                        return null;
                    }
                }).filter(m -> m != null).toList();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private String technicalId; // internal use, not exposed

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