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
        if (mail == null || !mail.isValid()) {
            log.error("Invalid Mail entity received.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);
        processMail(technicalId, mail);
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Mail entity retrieved for technicalId: {}", technicalId);
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail entity with technicalId: {}", technicalId);

        // Validate criteria if needed - here assumed always true for simplicity
        boolean happyCriteria = checkMailHappyCriteria(mail);
        boolean gloomyCriteria = checkMailGloomyCriteria(mail);

        try {
            if (happyCriteria && mail.isHappy()) {
                sendHappyMail(mail);
                log.info("Happy mail sent for technicalId: {}", technicalId);
            } else if (gloomyCriteria && !mail.isHappy()) {
                sendGloomyMail(mail);
                log.info("Gloomy mail sent for technicalId: {}", technicalId);
            } else {
                log.info("Mail with technicalId {} does not meet any sending criteria", technicalId);
            }
        } catch (Exception e) {
            log.error("Failed to send mail for technicalId: {}", technicalId, e);
        }
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        // Implement actual happy mail criteria logic here
        // For prototype, assume if isHappy is true, criteria is met
        return mail.isHappy();
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // Implement actual gloomy mail criteria logic here
        // For prototype, assume if isHappy is false, criteria is met
        return !mail.isHappy();
    }

    private void sendHappyMail(Mail mail) {
        // Implement sending happy mail logic here
        // For prototype, simulate sending by logging
        log.info("Sending happy mail to recipients: {}", mail.getMailList());
    }

    private void sendGloomyMail(Mail mail) {
        // Implement sending gloomy mail logic here
        // For prototype, simulate sending by logging
        log.info("Sending gloomy mail to recipients: {}", mail.getMailList());
    }
}