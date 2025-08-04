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
        if (mail == null || mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Invalid mail entity: mailList is empty or null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList must not be empty"));
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail created with technicalId {}", technicalId);

        processMail(technicalId, mail);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<?> getMail(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail with technicalId {} not found", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        }
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing mail with technicalId {}", technicalId);

        // Example criteria checks (simplified)
        boolean happyCriteria = checkMailHappyCriteria(mail);
        boolean gloomyCriteria = checkMailGloomyCriteria(mail);

        if (happyCriteria) {
            sendHappyMail(mail);
        } else if (gloomyCriteria) {
            sendGloomyMail(mail);
        } else {
            log.warn("Mail with technicalId {} did not meet any criteria", technicalId);
        }

        log.info("Completed processing mail with technicalId {}", technicalId);
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        // Business rule: isHappy must be true and mailList not empty
        return mail.isHappy() && mail.getMailList() != null && !mail.getMailList().isEmpty();
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // Business rule: isHappy must be false and mailList not empty
        return !mail.isHappy() && mail.getMailList() != null && !mail.getMailList().isEmpty();
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mails to recipients
        for (String recipient : mail.getMailList()) {
            log.info("Sending HAPPY mail to {}", recipient);
            // Here you would integrate with actual email sending service
        }
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mails to recipients
        for (String recipient : mail.getMailList()) {
            log.info("Sending GLOOMY mail to {}", recipient);
            // Here you would integrate with actual email sending service
        }
    }
}