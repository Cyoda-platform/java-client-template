package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Mail;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    @PostMapping("/mails")
    public ResponseEntity<?> createMail(@RequestBody Map<String, Object> requestBody) {
        try {
            Object mailListObj = requestBody.get("mailList");
            if (mailListObj == null || !(mailListObj instanceof List)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Invalid or missing 'mailList' field");
            }
            List<String> mailList = new ArrayList<>();
            for (Object obj : (List<?>) mailListObj) {
                if (!(obj instanceof String) || ((String) obj).isBlank()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Each email in 'mailList' must be a non-blank string");
                }
                mailList.add((String) obj);
            }

            Mail mail = new Mail();
            String id = String.valueOf(mailIdCounter.getAndIncrement());
            mail.setId(id);
            mail.setTechnicalId(UUID.randomUUID());
            mail.setMailList(mailList);
            mail.setIsHappy(null);
            mail.setIsGloomy(null);
            mail.setCriteriaResults(new HashMap<>());
            mail.setStatus(null != null ? null : null); // Keep as null initially

            mailCache.put(id, mail);
            log.info("Created Mail with ID: {}", id);

            // Trigger processing
            processMail(mail);

            Map<String, String> response = new HashMap<>();
            response.put("id", id);
            response.put("status", mail.getStatus() != null ? mail.getStatus().name() : "null");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error creating mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal error occurred");
        }
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<?> getMail(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Mail with ID " + id + " not found");
        }
        return ResponseEntity.ok(mail);
    }

    @PostMapping("/mails/{id}/update")
    public ResponseEntity<?> updateMail(@PathVariable String id, @RequestBody Map<String, Object> requestBody) {
        Mail existingMail = mailCache.get(id);
        if (existingMail == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Mail with ID " + id + " not found");
        }
        try {
            Object mailListObj = requestBody.get("mailList");
            if (mailListObj == null || !(mailListObj instanceof List)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Invalid or missing 'mailList' field");
            }
            List<String> mailList = new ArrayList<>();
            for (Object obj : (List<?>) mailListObj) {
                if (!(obj instanceof String) || ((String) obj).isBlank()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Each email in 'mailList' must be a non-blank string");
                }
                mailList.add((String) obj);
            }
            Mail newMailVersion = new Mail();
            String newId = String.valueOf(mailIdCounter.getAndIncrement());
            newMailVersion.setId(newId);
            newMailVersion.setTechnicalId(UUID.randomUUID());
            newMailVersion.setMailList(mailList);
            newMailVersion.setIsHappy(null);
            newMailVersion.setIsGloomy(null);
            newMailVersion.setCriteriaResults(new HashMap<>());
            newMailVersion.setStatus(null != null ? null : null); // Keep as null initially

            mailCache.put(newId, newMailVersion);
            log.info("Created new version Mail with ID: {}", newId);

            // Trigger processing
            processMail(newMailVersion);

            Map<String, String> response = new HashMap<>();
            response.put("id", newId);
            response.put("status", newMailVersion.getStatus() != null ? newMailVersion.getStatus().name() : "null");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error updating mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal error occurred");
        }
    }

    @PostMapping("/mails/{id}/deactivate")
    public ResponseEntity<?> deactivateMail(@PathVariable String id) {
        Mail existingMail = mailCache.get(id);
        if (existingMail == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Mail with ID " + id + " not found");
        }
        try {
            // Create deactivation record as a new entity version with status DEACTIVATED
            Mail deactivationRecord = new Mail();
            String newId = String.valueOf(mailIdCounter.getAndIncrement());
            deactivationRecord.setId(newId);
            deactivationRecord.setTechnicalId(UUID.randomUUID());
            deactivationRecord.setMailList(existingMail.getMailList());
            deactivationRecord.setIsHappy(existingMail.getIsHappy());
            deactivationRecord.setIsGloomy(existingMail.getIsGloomy());
            deactivationRecord.setCriteriaResults(existingMail.getCriteriaResults());
            // DEACTIVATED status is not in enum, so set to null or handle differently
            deactivationRecord.setStatus(null);

            mailCache.put(newId, deactivationRecord);
            log.info("Deactivated Mail with new record ID: {}", newId);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Mail deactivated with new record ID: " + newId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error deactivating mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal error occurred");
        }
    }

    private void processMail(Mail mail) {
        log.info("Processing Mail with ID: {}", mail.getId());
        // Step 1: Evaluate all 22 criteria, for prototype we simulate evaluation:
        Map<String, String> criteriaResults = new HashMap<>();
        int happyCount = 0;
        int gloomyCount = 0;
        for (int i = 1; i <= 22; i++) {
            // Simulated logic: even criteria are "isHappy", odd criteria "isGloomy"
            String result = (i % 2 == 0) ? "isHappy" : "isGloomy";
            criteriaResults.put("criteria" + i, result);
            if ("isHappy".equals(result)) happyCount++;
            else gloomyCount++;
        }
        mail.setCriteriaResults(criteriaResults);

        // Step 2: Determine overall mood
        if (happyCount > gloomyCount) {
            mail.setIsHappy(true);
            mail.setIsGloomy(false);
            mail.setStatus(null); // cannot set PROCESSING because enum is not accessible
        } else {
            mail.setIsHappy(false);
            mail.setIsGloomy(true);
            mail.setStatus(null); // cannot set PROCESSING because enum is not accessible
        }

        // Step 3: Send mail via appropriate processor (simulated)
        boolean sendSuccess = true;
        if (mail.getIsHappy()) {
            // simulate sendHappyMail
            log.info("Sending happy mail to recipients: {}", mail.getMailList());
            mail.setStatus(null); // cannot set SENT_HAPPY because enum is not accessible
        } else {
            // simulate sendGloomyMail
            log.info("Sending gloomy mail to recipients: {}", mail.getMailList());
            mail.setStatus(null); // cannot set SENT_GLOOMY because enum is not accessible
        }

        // Step 4: Handle send failure (simulated always success here)
        if (!sendSuccess) {
            mail.setStatus(null); // cannot set FAILED because enum is not accessible
            log.error("Failed to send mail with ID: {}", mail.getId());
        }
    }
}
