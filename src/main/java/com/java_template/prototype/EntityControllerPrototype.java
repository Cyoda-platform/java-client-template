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
        // Validate input
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail list is empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList cannot be empty"));
        }
        if (mail.getIsHappy() == null) {
            log.error("isHappy field is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "isHappy must be provided"));
        }

        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mail.setStatus("PENDING");
        mailCache.put(technicalId, mail);

        log.info("Created Mail entity with technicalId: {}", technicalId);

        processMail(technicalId, mail);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail with technicalId {} not found", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        }
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail with ID: {}", technicalId);

        // Validation already done on create, but revalidate if needed here

        try {
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(mail);
            } else {
                sendGloomyMail(mail);
            }
            mail.setStatus("SENT");
            log.info("Mail with ID {} sent successfully", technicalId);
        } catch (Exception e) {
            mail.setStatus("FAILED");
            log.error("Failed to send Mail with ID {}: {}", technicalId, e.getMessage());
        }
        // Cache update to reflect status change (immutable pattern suggests creating new event, but for prototype we update cache)
        mailCache.put(technicalId, mail);
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail content
        log.info("Sending HAPPY mail to recipients: {}", mail.getMailList());
        // Actual mail sending logic would go here (e.g. JavaMailSender)
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail content
        log.info("Sending GLOOMY mail to recipients: {}", mail.getMailList());
        // Actual mail sending logic would go here (e.g. JavaMailSender)
    }
}