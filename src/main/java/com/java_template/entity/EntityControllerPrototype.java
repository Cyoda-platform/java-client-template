package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/messages")
public class EntityControllerPrototype {

    private final Map<String, Message> messageStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Message createMessage(@RequestBody @Valid CreateMessageRequest request) {
        log.info("Received create message request: {}", request.getText());
        String id = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        Message message = new Message(id, request.getText(), timestamp.toString());
        messageStore.put(id, message);
        log.info("Message stored with id: {}", id);
        return message;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Message getMessageById(@PathVariable String id) {
        log.info("Fetching message by id: {}", id);
        Message message = messageStore.get(id);
        if (message == null) {
            log.error("Message not found for id: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        return message;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Message> getAllMessages() {
        log.info("Fetching all messages, count={}", messageStore.size());
        return new ArrayList<>(messageStore.values());
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        return error;
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

    // TODO: If future external API integrations or async processing are required,
    // implement here with @Async or CompletableFuture.runAsync(...)
}