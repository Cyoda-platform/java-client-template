package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) throws InterruptedException, ExecutionException {
        try {
            if (mail == null || !mail.isValid()) {
                log.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get(); // wait for completion

            log.info("Mail entity created with technicalId: {}", technicalId);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error creating Mail entity", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) throws InterruptedException, ExecutionException, JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("Mail entity not found for technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Convert ObjectNode to Mail
            Mail mail = objectMapper.treeToValue(node, Mail.class);
            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving Mail entity", e);
            throw e;
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}