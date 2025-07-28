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
        if (mail == null || !mail.isValid()) {
            log.error("Invalid Mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);

        try {
            processMail(technicalId, mail);
            log.info("Processed Mail entity with technicalId: {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing Mail entity with technicalId: {}, error: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<Mail> getMail(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Retrieved Mail entity with technicalId: {}", technicalId);
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        // Business logic: Decide which processor to use based on isHappy flag
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        // Implement sending happy-themed mails to all recipients
        for (String recipient : mail.getMailList()) {
            // Simulate sending mail
            log.info("Sending HAPPY mail to {} for technicalId {}", recipient, technicalId);
        }
        log.info("Completed sending HAPPY mails for technicalId {}", technicalId);
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        // Implement sending gloomy-themed mails to all recipients
        for (String recipient : mail.getMailList()) {
            // Simulate sending mail
            log.info("Sending GLOOMY mail to {} for technicalId {}", recipient, technicalId);
        }
        log.info("Completed sending GLOOMY mails for technicalId {}", technicalId);
    }
}