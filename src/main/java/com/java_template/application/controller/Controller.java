package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mails")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;

    private static final String ENTITY_NAME = "Mail";

    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Map<String, Object> requestBody) throws ExecutionException, InterruptedException {
        Object mailListObj = requestBody.get("mailList");
        if (mailListObj == null || !(mailListObj instanceof List)) {
            return ResponseEntity.badRequest().body("Invalid or missing 'mailList' field");
        }
        List<String> mailList = new ArrayList<>();
        for (Object obj : (List<?>) mailListObj) {
            if (!(obj instanceof String) || ((String) obj).isBlank()) {
                return ResponseEntity.badRequest().body("Each email in 'mailList' must be a non-blank string");
            }
            mailList.add((String) obj);
        }

        Mail mail = new Mail();
        mail.setMailList(mailList);
        mail.setIsHappy(null);
        mail.setIsGloomy(null);
        mail.setCriteriaResults(new HashMap<>());
        mail.setStatus(null);

        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, mail);
        UUID technicalId = idFuture.get(); // wait for completion
        mail.setTechnicalId(technicalId);

        processMail(mail);

        // Save processed mail as new version
        CompletableFuture<UUID> updatedIdFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, mail);
        UUID updatedTechnicalId = updatedIdFuture.get();
        mail.setTechnicalId(updatedTechnicalId);

        Map<String, String> response = new HashMap<>();
        response.put("id", updatedTechnicalId.toString());
        response.put("status", mail.getStatus() == null ? "null" : mail.getStatus());
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMail(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid UUID format for id");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
        ObjectNode result = itemFuture.get();
        if (result == null || result.isEmpty()) {
            return ResponseEntity.status(404).body("Mail with ID " + id + " not found");
        }
        // convert ObjectNode to Mail
        Mail mail = entityService.getObjectMapper().convertValue(result, Mail.class);
        return ResponseEntity.ok(mail);
    }

    @PostMapping("/{id}/update")
    public ResponseEntity<?> updateMail(@PathVariable String id, @RequestBody Map<String, Object> requestBody) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid UUID format for id");
        }
        CompletableFuture<ObjectNode> existingFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
        ObjectNode existingNode = existingFuture.get();
        if (existingNode == null || existingNode.isEmpty()) {
            return ResponseEntity.status(404).body("Mail with ID " + id + " not found");
        }

        Object mailListObj = requestBody.get("mailList");
        if (mailListObj == null || !(mailListObj instanceof List)) {
            return ResponseEntity.badRequest().body("Invalid or missing 'mailList' field");
        }
        List<String> mailList = new ArrayList<>();
        for (Object obj : (List<?>) mailListObj) {
            if (!(obj instanceof String) || ((String) obj).isBlank()) {
                return ResponseEntity.badRequest().body("Each email in 'mailList' must be a non-blank string");
            }
            mailList.add((String) obj);
        }

        Mail newMailVersion = entityService.getObjectMapper().convertValue(existingNode, Mail.class);
        newMailVersion.setTechnicalId(null); // clear technicalId to create new entity version
        newMailVersion.setMailList(mailList);
        newMailVersion.setIsHappy(null);
        newMailVersion.setIsGloomy(null);
        newMailVersion.setCriteriaResults(new HashMap<>());
        newMailVersion.setStatus(null);

        CompletableFuture<UUID> newIdFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, newMailVersion);
        UUID newTechnicalId = newIdFuture.get();
        newMailVersion.setTechnicalId(newTechnicalId);

        processMail(newMailVersion);

        // Save processed mail as new version
        CompletableFuture<UUID> processedIdFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, newMailVersion);
        UUID processedTechnicalId = processedIdFuture.get();
        newMailVersion.setTechnicalId(processedTechnicalId);

        Map<String, String> response = new HashMap<>();
        response.put("id", processedTechnicalId.toString());
        response.put("status", newMailVersion.getStatus() == null ? "null" : newMailVersion.getStatus());
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateMail(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body("Invalid UUID format for id");
        }
        CompletableFuture<ObjectNode> existingFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
        ObjectNode existingNode = existingFuture.get();
        if (existingNode == null || existingNode.isEmpty()) {
            return ResponseEntity.status(404).body("Mail with ID " + id + " not found");
        }

        Mail existingMail = entityService.getObjectMapper().convertValue(existingNode, Mail.class);

        Mail deactivationRecord = new Mail();
        deactivationRecord.setMailList(existingMail.getMailList());
        deactivationRecord.setIsHappy(existingMail.getIsHappy());
        deactivationRecord.setIsGloomy(existingMail.getIsGloomy());
        deactivationRecord.setCriteriaResults(existingMail.getCriteriaResults());
        deactivationRecord.setStatus(null);

        CompletableFuture<UUID> newIdFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, deactivationRecord);
        UUID newTechnicalId = newIdFuture.get();
        deactivationRecord.setTechnicalId(newTechnicalId);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Mail deactivated with new record ID: " + newTechnicalId);
        return ResponseEntity.ok(response);
    }

    private void processMail(Mail mail) {
        log.info("Processing Mail with technicalId: {}", mail.getTechnicalId());
        Map<String, String> criteriaResults = new HashMap<>();
        int happyCount = 0;
        int gloomyCount = 0;
        for (int i = 1; i <= 22; i++) {
            String result = (i % 2 == 0) ? "isHappy" : "isGloomy";
            criteriaResults.put("criteria" + i, result);
            if ("isHappy".equals(result)) happyCount++;
            else gloomyCount++;
        }
        mail.setCriteriaResults(criteriaResults);

        if (happyCount > gloomyCount) {
            mail.setIsHappy(true);
            mail.setIsGloomy(false);
            mail.setStatus(null);
        } else {
            mail.setIsHappy(false);
            mail.setIsGloomy(true);
            mail.setStatus(null);
        }

        boolean sendSuccess = true;
        if (mail.getIsHappy() != null && mail.getIsHappy()) {
            log.info("Sending happy mail to recipients: {}", mail.getMailList());
            mail.setStatus(null);
        } else {
            log.info("Sending gloomy mail to recipients: {}", mail.getMailList());
            mail.setStatus(null);
        }
        if (!sendSuccess) {
            mail.setStatus(null);
            log.error("Failed to send mail with technicalId: {}", mail.getTechnicalId());
        }
    }
}