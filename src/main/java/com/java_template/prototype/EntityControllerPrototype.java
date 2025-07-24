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
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        // Validate input
        if (mail == null) {
            log.error("Received null mail in createMail");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Mail cannot be null"));
        }
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail list is empty or null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "mailList must contain at least one email address"));
        }
        for (String email : mail.getMailList()) {
            if (email == null || email.isBlank()) {
                log.error("Invalid email in mailList: '{}'", email);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "mailList contains blank or null email"));
            }
        }

        // Generate technicalId
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mail.setId(technicalId); // set id field for internal use

        // Save immutable entity
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with ID: {}", technicalId);

        // Trigger processing
        processMail(mail);

        // Return technicalId only
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/mail/{id}")
    public ResponseEntity<?> getMailById(@PathVariable("id") String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Mail not found for technicalId: " + id));
        }
        return ResponseEntity.ok(mail);
    }

    // Core business logic as per event-driven architecture
    private void processMail(Mail mail) {
        log.info("Processing Mail with ID: {}", mail.getId());

        // Validation criteria (if explicitly requested - here always executed)
        boolean isHappyValid = checkMailHappy(mail);
        boolean isGloomyValid = checkMailGloomy(mail);

        // Routing based on criteria results
        if (isHappyValid) {
            processMailSendHappyMail(mail);
        } else if (isGloomyValid) {
            processMailSendGloomyMail(mail);
        } else {
            log.error("Mail with ID {} does not meet any criteria for processing", mail.getId());
        }
    }

    private boolean checkMailHappy(Mail mail) {
        // Criteria: isHappy == true
        boolean result = Boolean.TRUE.equals(mail.getIsHappy());
        log.info("checkMailHappy for ID {}: {}", mail.getId(), result);
        return result;
    }

    private boolean checkMailGloomy(Mail mail) {
        // Criteria: isHappy == false
        boolean result = Boolean.FALSE.equals(mail.getIsHappy());
        log.info("checkMailGloomy for ID {}: {}", mail.getId(), result);
        return result;
    }

    private void processMailSendHappyMail(Mail mail) {
        // Business logic: send happy mail
        log.info("Sending HAPPY mail to recipients: {} for Mail ID: {}", mail.getMailList(), mail.getId());

        // Simulate sending mail - in real app, integrate with mail service
        for (String recipient : mail.getMailList()) {
            log.info("Happy mail sent to {}", recipient);
        }
    }

    private void processMailSendGloomyMail(Mail mail) {
        // Business logic: send gloomy mail
        log.info("Sending GLOOMY mail to recipients: {} for Mail ID: {}", mail.getMailList(), mail.getId());

        // Simulate sending mail - in real app, integrate with mail service
        for (String recipient : mail.getMailList()) {
            log.info("Gloomy mail sent to {}", recipient);
        }
    }
}