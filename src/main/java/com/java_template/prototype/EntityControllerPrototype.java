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

    // POST /prototype/mail - create Mail entity, trigger processing, return technicalId
    @PostMapping("/mail")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            // Validate required fields
            if (mail.getIsHappy() == null) {
                log.error("Validation failed: isHappy is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Field 'isHappy' is required"));
            }
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                log.error("Validation failed: mailList is required and cannot be empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Field 'mailList' is required and cannot be empty"));
            }
            for (String email : mail.getMailList()) {
                if (email == null || email.isBlank()) {
                    log.error("Validation failed: mailList contains blank email");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList cannot contain blank email addresses"));
                }
            }

            // Generate technicalId as string of incrementing id
            String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
            // Save entity in cache keyed by technicalId
            mailCache.put(technicalId, mail);

            log.info("Mail entity created with technicalId: {}", technicalId);

            // Trigger processing
            processMail(mail);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (Exception e) {
            log.error("Error creating Mail entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /prototype/mail/{id} - retrieve Mail entity by technicalId
    @GetMapping("/mail/{id}")
    public ResponseEntity<?> getMailById(@PathVariable("id") String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail entity not found for id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail entity not found"));
        }
        log.info("Retrieved Mail entity for id: {}", id);
        return ResponseEntity.ok(mail);
    }

    // Core processing method for Mail entity
    private void processMail(Mail mail) {
        log.info("Processing Mail entity with mailList size: {}", mail.getMailList().size());

        // Validate criteria and invoke corresponding processor
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            if (checkMailHappy(mail)) {
                sendHappyMail(mail);
            } else {
                log.error("Mail did not pass happy mail criteria");
            }
        } else {
            if (checkMailGloomy(mail)) {
                sendGloomyMail(mail);
            } else {
                log.error("Mail did not pass gloomy mail criteria");
            }
        }
    }

    // Criteria check for happy mail
    private boolean checkMailHappy(Mail mail) {
        // isHappy true means happy mail
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    // Criteria check for gloomy mail
    private boolean checkMailGloomy(Mail mail) {
        // isHappy false means gloomy mail
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    // Processor for sending happy mail
    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail - real implementation would send emails here
        log.info("Sending HAPPY mail to recipients: {}", mail.getMailList());
        // Business logic like sending email, updating status, notifications, etc.
    }

    // Processor for sending gloomy mail
    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail - real implementation would send emails here
        log.info("Sending GLOOMY mail to recipients: {}", mail.getMailList());
        // Business logic like sending email, updating status, notifications, etc.
    }
}