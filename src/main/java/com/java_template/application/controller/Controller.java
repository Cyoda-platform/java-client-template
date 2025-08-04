package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping(path = "/mails")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@Valid @RequestBody Mail mail) throws ExecutionException, InterruptedException, TimeoutException {
        try {
            if (!mail.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            // Add item via entityService
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get(5, TimeUnit.SECONDS);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) throws ExecutionException, InterruptedException, TimeoutException, JsonProcessingException {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get(5, TimeUnit.SECONDS);
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Mail mail = objectMapper.treeToValue(node, Mail.class);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{technicalId}")
    public ResponseEntity<Void> updateMail(@PathVariable String technicalId, @Valid @RequestBody Mail mail) throws ExecutionException, InterruptedException, TimeoutException {
        try {
            if (!mail.isValid()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<Void> updateFuture = entityService.updateItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalUUID, mail);
            updateFuture.get(5, TimeUnit.SECONDS);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @DeleteMapping("/{technicalId}")
    public ResponseEntity<String> deleteMail(@PathVariable String technicalId) throws ExecutionException, InterruptedException, TimeoutException {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<UUID> deleteFuture = entityService.deleteItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            UUID deletedId = deleteFuture.get(5, TimeUnit.SECONDS);
            return ResponseEntity.ok(deletedId.toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // DTO class for API input/output
    public static class Mail {
        private Boolean isHappy;
        private List<String> mailList;

        public Boolean getIsHappy() {
            return isHappy;
        }

        public void setIsHappy(Boolean isHappy) {
            this.isHappy = isHappy;
        }

        public List<String> getMailList() {
            return mailList;
        }

        public void setMailList(List<String> mailList) {
            this.mailList = mailList;
        }

        public boolean isValid() {
            if (isHappy == null) {
                return false;
            }
            if (mailList == null || mailList.isEmpty()) {
                return false;
            }
            for (String mail : mailList) {
                if (mail == null || mail.isBlank()) {
                    return false;
                }
            }
            return true;
        }
    }
}