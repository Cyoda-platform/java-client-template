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
        if (!mail.isValid()) {
            log.error("Validation failed for Mail entity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        try {
            processMail(technicalId, mail);
            log.info("Mail processed successfully with id {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing Mail entity with id {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail entity not found with id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        // Validation: check mailList not empty and content not blank
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail list is empty for mail id {}", technicalId);
            throw new IllegalArgumentException("mailList cannot be empty");
        }
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Mail content is blank for mail id {}", technicalId);
            throw new IllegalArgumentException("content cannot be blank");
        }
        // Processing: send happy or gloomy mail based on isHappy flag
        if (mail.isHappy()) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }
        // Completion: persist sending status if needed (simulate here)
        log.info("Mail with id {} sent successfully. isHappy={}", technicalId, mail.isHappy());
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            // Simulate sending happy mail
            log.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
        }
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            // Simulate sending gloomy mail
            log.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
        }
    }
}