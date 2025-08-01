package com.java_template.prototype;

import com.java_template.application.entity.Mail;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    // Using UUID as technicalId, no need for AtomicLong for the ID itself, but keeping it for completeness if needed for other entities
    // private final AtomicLong mailIdCounter = new AtomicLong(1);

    @PostMapping("/mails")
    public ResponseEntity<String> createMail(@RequestBody Mail newMail) {
        try {
            if (!newMail.isValid()) {
                log.error("Invalid Mail entity provided: {}", newMail);
                return new ResponseEntity<>("Invalid Mail entity (mailList cannot be empty)", HttpStatus.BAD_REQUEST);
            }

            String technicalId = UUID.randomUUID().toString();
            // In a real scenario, the mail entity might get additional fields like status, timestamp here
            // For this prototype, we'll just set the initial state and let processMail handle isHappy
            mailCache.put(technicalId, newMail);
            log.info("Mail entity created with technicalId: {}", technicalId);

            // Simulate Cyoda automatically calling processMail() after saving the entity
            processMail(technicalId, newMail);

            return new ResponseEntity<>(technicalId, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error creating mail entity: {}", e.getMessage(), e);
            return new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.warn("Mail entity with technicalId {} not found.", id);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        log.info("Retrieved Mail entity with technicalId: {}", id);
        return new ResponseEntity<>(mail, HttpStatus.OK);
    }

    // Criteria methods
    private boolean checkMailIsHappy(Mail mail) {
        // Placeholder logic: mail is happy if mailList has an even number of recipients
        boolean isHappy = mail.getMailList() != null && mail.getMailList().size() % 2 == 0;
        log.info("CheckMailIsHappy for mail with {} recipients: {}", mail.getMailList().size(), isHappy);
        return isHappy;
    }

    private boolean checkMailIsGloomy(Mail mail) {
        // Placeholder logic: mail is gloomy if mailList has an odd number of recipients
        boolean isGloomy = mail.getMailList() != null && mail.getMailList().size() % 2 != 0;
        log.info("CheckMailIsGloomy for mail with {} recipients: {}", mail.getMailList().size(), isGloomy);
        return isGloomy;
    }

    // Processor methods
    private void sendHappyMail(Mail mail) {
        log.info("Sending happy mail to: {}", mail.getMailList());
        // Simulate external API call or actual mail sending
        // In a real application, this would involve a mail client or service
    }

    private void sendGloomyMail(Mail mail) {
        log.info("Sending gloomy mail to: {}", mail.getMailList());
        // Simulate external API call or actual mail sending
        // In a real application, this would involve a mail client or service
    }

    // Main process method triggered by entity creation
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail entity with technicalId: {}", technicalId);
        try {
            // Criteria Evaluation
            boolean isHappy = checkMailIsHappy(mail);
            boolean isGloomy = checkMailIsGloomy(mail); // This will be the opposite of isHappy based on current logic

            if (isHappy) {
                mail.setIsHappy(true);
                sendHappyMail(mail);
                log.info("Mail {} classified as HAPPY. Status: HAPPY_MAIL_SENT", technicalId);
            } else if (isGloomy) {
                mail.setIsHappy(false);
                sendGloomyMail(mail);
                log.info("Mail {} classified as GLOOMY. Status: GLOOMY_MAIL_SENT", technicalId);
            } else {
                // Should not happen with current simple logic, but good for robustness
                log.warn("Mail {} could not be classified as happy or gloomy. Status: UNCLASSIFIED", technicalId);
            }
            // Update the mail entity in the cache with the determined isHappy status
            mailCache.put(technicalId, mail);

        } catch (Exception e) {
            log.error("Error processing mail entity {}: {}", technicalId, e.getMessage(), e);
            // In a real system, you might update the mail status to FAILED here
            // mail.setStatus("FAILED");
            // mailCache.put(technicalId, mail);
        }
    }
}