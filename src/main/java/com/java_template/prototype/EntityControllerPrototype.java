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
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail creation failed: mailList is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("mailList is required and cannot be empty");
        }

        String id = String.valueOf(mailIdCounter.getAndIncrement());
        mail.setId(id);
        mail.setStatus("PENDING");
        mailCache.put(id, mail);

        log.info("Mail created with ID: {}", id);

        try {
            processMail(mail);
        } catch (Exception e) {
            log.error("Processing mail failed for ID {}: {}", id, e.getMessage());
            mail.setStatus("FAILED");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }

        mailCache.put(id, mail);
        return ResponseEntity.status(HttpStatus.CREATED).body(mail);
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<?> getMail(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail not found");
        }
        return ResponseEntity.ok(mail);
    }

    private void processMail(Mail mail) {
        log.info("Processing Mail with ID: {}", mail.getId());

        boolean isHappy = false;
        boolean happyCriteriaMet = checkEntityIsHappy(mail);
        boolean gloomyCriteriaMet = checkEntityIsGloomy(mail);

        if (happyCriteriaMet) {
            isHappy = true;
            mail.setIsHappy(true);
            processMailSendHappyMail(mail);
            mail.setStatus("SENT_HAPPY");
            log.info("Mail ID {} sent as Happy", mail.getId());
        } else if (gloomyCriteriaMet) {
            isHappy = false;
            mail.setIsHappy(false);
            processMailSendGloomyMail(mail);
            mail.setStatus("SENT_GLOOMY");
            log.info("Mail ID {} sent as Gloomy", mail.getId());
        } else {
            mail.setStatus("FAILED");
            log.error("Mail ID {} does not meet any criteria for sending", mail.getId());
            throw new IllegalStateException("Mail does not meet happy or gloomy criteria");
        }
    }

    private boolean checkEntityIsHappy(Mail mail) {
        // Implement actual 22 criteria checks here; for prototype, simulate with simple logic
        // Example: if mailList size > 1, happy
        return mail.getMailList() != null && mail.getMailList().size() > 1;
    }

    private boolean checkEntityIsGloomy(Mail mail) {
        // Implement actual gloomy criteria checks here; for prototype, simulate with simple logic
        // Example: if mailList size == 1, gloomy
        return mail.getMailList() != null && mail.getMailList().size() == 1;
    }

    private void processMailSendHappyMail(Mail mail) {
        // Simulate sending happy mail
        log.info("Sending Happy Mail to recipients: {}", mail.getMailList());
        // Real implementation would send emails here
    }

    private void processMailSendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending Gloomy Mail to recipients: {}", mail.getMailList());
        // Real implementation would send emails here
    }
}