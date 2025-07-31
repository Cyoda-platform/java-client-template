package com.java_template.application.controller;

import com.java_template.application.entity.Mail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/api")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // CRUD operations for Mail entity

    @PostMapping("/mail")
    public ResponseEntity<Mail> createMail(@Valid @RequestBody Mail mail) throws ExecutionException, InterruptedException {
        UUID createdId = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail).get();
        mail.setTechnicalId(createdId);
        return ResponseEntity.ok(mail);
    }

    @GetMapping("/mail/{id}")
    public ResponseEntity<Mail> getMail(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(id);
        ObjectNode node = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, uuid).get();
        Mail mail = objectMapper.treeToValue(node, Mail.class);
        return ResponseEntity.ok(mail);
    }

    @PutMapping("/mail/{id}")
    public ResponseEntity<Mail> updateMail(@PathVariable String id, @Valid @RequestBody Mail mail) throws ExecutionException, InterruptedException {
        UUID uuid = UUID.fromString(id);
        entityService.updateItem(Mail.ENTITY_NAME, ENTITY_VERSION, uuid, mail).get();
        mail.setTechnicalId(uuid);
        return ResponseEntity.ok(mail);
    }

    @DeleteMapping("/mail/{id}")
    public ResponseEntity<String> deleteMail(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID uuid = UUID.fromString(id);
        UUID deletedId = entityService.deleteItem(Mail.ENTITY_NAME, ENTITY_VERSION, uuid).get();
        return ResponseEntity.ok(deletedId.toString());
    }

    // Cache for Mail
    private Mail mailCache;

    // Save Mail to cache
    private void saveMailToCache(Mail mail) {
        this.mailCache = mail;
    }

    // Other unrelated methods
    public void unrelatedMethod() {
        // Some code
    }
}