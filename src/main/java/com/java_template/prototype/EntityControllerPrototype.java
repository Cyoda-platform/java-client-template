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
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            // Validate input
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                log.error("Mail list cannot be empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "mailList must not be empty"));
            }
            if (mail.getStatus() != null && mail.getStatus().isBlank() == false) {
                // allow status to be null on creation, but if present must not be blank
            } else {
                mail.setStatus("CREATED");
            }

            // Generate technicalId
            String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
            mail.setId(technicalId);

            mailCache.put(technicalId, mail);

            processMail(mail);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("technicalId", technicalId));
        } catch (Exception e) {
            log.error("Failed to create Mail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<?> getMailById(@PathVariable("id") String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Mail not found"));
        }
        return ResponseEntity.ok(mail);
    }

    @GetMapping("/mails")
    public ResponseEntity<List<Mail>> getMailsByIsHappy(@RequestParam(name = "isHappy", required = false) Boolean isHappy) {
        List<Mail> filteredMails = new ArrayList<>();
        for (Mail mail : mailCache.values()) {
            if (isHappy == null || (mail.getIsHappy() != null && mail.getIsHappy().equals(isHappy))) {
                filteredMails.add(mail);
            }
        }
        return ResponseEntity.ok(filteredMails);
    }

    private void processMail(Mail mail) {
        log.info("Processing Mail with ID: {}", mail.getId());

        // Step 2: Validation - Determine isHappy if not set
        if (mail.getIsHappy() == null) {
            boolean happyCriteria = checkMailHappyCriteria(mail);
            boolean gloomyCriteria = checkMailGloomyCriteria(mail);
            if (happyCriteria) {
                mail.setIsHappy(true);
            } else if (gloomyCriteria) {
                mail.setIsHappy(false);
            } else {
                mail.setIsHappy(false); // Default to gloomy if no criteria matched
            }
        }

        // Step 3: Processing according to isHappy
        boolean sendSuccess = false;
        try {
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendSuccess = sendHappyMail(mail);
            } else {
                sendSuccess = sendGloomyMail(mail);
            }
        } catch (Exception e) {
            log.error("Error sending mail with ID: {}", mail.getId(), e);
        }

        // Step 4: Completion - update status
        if (sendSuccess) {
            mail.setStatus("SENT");
            log.info("Mail with ID: {} sent successfully", mail.getId());
        } else {
            mail.setStatus("FAILED");
            log.error("Mail with ID: {} failed to send", mail.getId());
        }

        // Update cache with new status
        mailCache.put(mail.getId(), mail);

        // Step 5: Optional notification/logging done via log statements above
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        // Example criteria: if mailList contains more than 2 recipients, consider happy
        if (mail.getMailList() != null && mail.getMailList().size() > 2) {
            log.info("Mail ID: {} meets happy criteria", mail.getId());
            return true;
        }
        return false;
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // Example criteria: if mailList has 1 or 0 recipients, consider gloomy
        if (mail.getMailList() != null && mail.getMailList().size() <= 2) {
            log.info("Mail ID: {} meets gloomy criteria", mail.getId());
            return true;
        }
        return false;
    }

    private boolean sendHappyMail(Mail mail) {
        // Simulate sending happy mail
        log.info("Sending happy mail to recipients: {}", mail.getMailList());
        // Implement actual mail sending logic here
        return true;
    }

    private boolean sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending gloomy mail to recipients: {}", mail.getMailList());
        // Implement actual mail sending logic here
        return true;
    }
}