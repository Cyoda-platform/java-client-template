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
        // Validate required fields
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail list is empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList must not be empty"));
        }
        if (mail.getIsHappy() == null) {
            log.error("isHappy flag is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "isHappy must be set"));
        }

        // Generate technical ID as string
        String id = String.valueOf(mailIdCounter.getAndIncrement());
        // Set initial status to PENDING
        mail.setStatus("PENDING");
        // Save to cache
        mailCache.put(id, mail);

        log.info("Created Mail with ID: {}", id);

        // Trigger processing event
        processMail(id, mail);

        // Return only technicalId
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<Object> getMailById(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        }
        return ResponseEntity.ok(mail);
    }

    // processMail method implementing business logic
    private void processMail(String id, Mail mail) {
        log.info("Processing Mail with ID: {}", id);

        // Validation: mailList not empty and isHappy set (already done in controller, but double-check)
        if (mail.getMailList() == null || mail.getMailList().isEmpty() || mail.getIsHappy() == null) {
            log.error("Invalid mail data for ID: {}", id);
            mail.setStatus("FAILED");
            mailCache.put(id, mail);
            return;
        }

        try {
            // Choose processor based on isHappy flag
            if (mail.getIsHappy()) {
                sendHappyMail(mail);
            } else {
                sendGloomyMail(mail);
            }
            mail.setStatus("SENT");
            log.info("Mail with ID {} sent successfully", id);
        } catch (Exception e) {
            log.error("Failed to send mail with ID {}: {}", id, e.getMessage());
            mail.setStatus("FAILED");
        }

        // Update cache with final status
        mailCache.put(id, mail);
    }

    // Simulated processor methods
    private void sendHappyMail(Mail mail) {
        log.info("sendHappyMail processor invoked for mailList: {}", mail.getMailList());
        // Simulate sending happy mail - real implementation would send emails here
    }

    private void sendGloomyMail(Mail mail) {
        log.info("sendGloomyMail processor invoked for mailList: {}", mail.getMailList());
        // Simulate sending gloomy mail - real implementation would send emails here
    }
}