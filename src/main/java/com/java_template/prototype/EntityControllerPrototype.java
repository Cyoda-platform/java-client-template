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

    @PostMapping("/mail")
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail creation failed: mailList is empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("mailList is required and cannot be empty");
        }
        for (String email : mail.getMailList()) {
            if (email == null || email.isBlank()) {
                log.error("Mail creation failed: mailList contains blank email");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("mailList contains invalid email");
            }
        }
        // Generate business ID as string of atomic counter
        String newId = String.valueOf(mailIdCounter.getAndIncrement());
        mail.setId(newId);
        mail.setTechnicalId(UUID.randomUUID());
        mail.setStatus("CREATED");
        mail.setIsHappy(null);
        mail.setCriteriaMatchedCount(0);

        mailCache.put(newId, mail);
        log.info("Created Mail entity with ID: {}", newId);

        processMail(mail); // Trigger processing

        return ResponseEntity.status(HttpStatus.CREATED).body(mail);
    }

    @GetMapping("/mail/{id}")
    public ResponseEntity<?> getMailById(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail not found");
        }
        return ResponseEntity.ok(mail);
    }

    @PostMapping("/mail/{id}/update")
    public ResponseEntity<?> updateMail(@PathVariable String id, @RequestBody Mail updatedMail) {
        Mail existingMail = mailCache.get(id);
        if (existingMail == null) {
            log.error("Mail update failed: Mail not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail not found");
        }
        if (updatedMail.getMailList() == null || updatedMail.getMailList().isEmpty()) {
            log.error("Mail update failed: mailList is empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("mailList is required and cannot be empty");
        }
        for (String email : updatedMail.getMailList()) {
            if (email == null || email.isBlank()) {
                log.error("Mail update failed: mailList contains blank email");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("mailList contains invalid email");
            }
        }
        // Create new version of the entity
        String newId = String.valueOf(mailIdCounter.getAndIncrement());
        updatedMail.setId(newId);
        updatedMail.setTechnicalId(UUID.randomUUID());
        updatedMail.setStatus("CREATED");
        updatedMail.setIsHappy(null);
        updatedMail.setCriteriaMatchedCount(0);

        mailCache.put(newId, updatedMail);
        log.info("Created new version of Mail entity with ID: {}", newId);

        processMail(updatedMail); // Trigger processing

        return ResponseEntity.status(HttpStatus.CREATED).body(updatedMail);
    }

    @PostMapping("/mail/{id}/deactivate")
    public ResponseEntity<?> deactivateMail(@PathVariable String id) {
        Mail existingMail = mailCache.get(id);
        if (existingMail == null) {
            log.error("Mail deactivate failed: Mail not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail not found");
        }
        // Create deactivation record as new entity
        String newId = String.valueOf(mailIdCounter.getAndIncrement());
        Mail deactivatedMail = new Mail();
        deactivatedMail.setId(newId);
        deactivatedMail.setTechnicalId(UUID.randomUUID());
        deactivatedMail.setMailList(existingMail.getMailList());
        deactivatedMail.setIsHappy(existingMail.getIsHappy());
        deactivatedMail.setCriteriaMatchedCount(existingMail.getCriteriaMatchedCount());
        deactivatedMail.setStatus("DEACTIVATED");

        mailCache.put(newId, deactivatedMail);
        log.info("Deactivated Mail entity with new ID: {}", newId);

        return ResponseEntity.ok("Mail entity deactivated with new ID: " + newId);
    }

    private void processMail(Mail mail) {
        log.info("Processing Mail with ID: {}", mail.getId());

        // Step 1: Validate mailList again (redundant but safe)
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Processing failed: mailList is empty");
            mail.setStatus("FAILED");
            return;
        }
        for (String email : mail.getMailList()) {
            if (email == null || email.isBlank()) {
                log.error("Processing failed: mailList contains invalid email");
                mail.setStatus("FAILED");
                return;
            }
        }

        // Step 2: Evaluate 22 criteria (simulate with random count for prototype)
        int matchedCriteriaCount = 0;
        Random random = new Random();
        matchedCriteriaCount = random.nextInt(23); // 0 to 22
        mail.setCriteriaMatchedCount(matchedCriteriaCount);

        // Step 3: Determine isHappy based on criteria (e.g., >=11 is happy)
        boolean isHappy = matchedCriteriaCount >= 11;
        mail.setIsHappy(isHappy);

        // Step 4: Trigger sendHappyMail or sendGloomyMail
        if (isHappy) {
            sendHappyMail(mail);
        } else {
            sendGloomyMail(mail);
        }

        // Step 5: Update status
        mail.setStatus("SENT");

        log.info("Mail with ID: {} processed and sent as {}", mail.getId(), isHappy ? "happy" : "gloomy");
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail
        log.info("Sending happy mail to recipients: {}", mail.getMailList());
        // In real implementation, send actual emails here
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending gloomy mail to recipients: {}", mail.getMailList());
        // In real implementation, send actual emails here
    }
}