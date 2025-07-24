package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Mail;
import java.util.UUID;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    @PostMapping("/mails")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        log.info("Received request to create Mail");

        // Validate input fields except id and technicalId which will be generated
        if (mail.getIsHappy() == null) {
            log.error("Validation failed: isHappy is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "isHappy is required"));
        }
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Validation failed: mailList is empty or null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList must contain at least one email"));
        }
        for (String email : mail.getMailList()) {
            if (email == null || email.isBlank()) {
                log.error("Validation failed: mailList contains blank email");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList cannot contain blank emails"));
            }
        }
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Validation failed: content is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "content is required"));
        }

        // Generate business id and technical id
        String generatedId = "M-" + mailIdCounter.getAndIncrement();
        mail.setId(generatedId);
        mail.setTechnicalId(UUID.randomUUID());
        mail.setStatus("PENDING");

        mailCache.put(generatedId, mail);

        // Trigger event-driven processing
        processMail(mail);

        // Return only technical id as per spec
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", mail.getTechnicalId().toString()));
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<?> getMail(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        }
        return ResponseEntity.ok(mail);
    }

    private void processMail(Mail mail) {
        log.info("Processing Mail with ID: {}", mail.getId());

        // Validation criteria
        if (checkMailIsHappy(mail)) {
            sendHappyMail(mail);
        } else if (checkMailIsGloomy(mail)) {
            sendGloomyMail(mail);
        } else {
            log.error("Mail with ID {} does not meet happy or gloomy criteria", mail.getId());
            mail.setStatus("FAILED");
        }
    }

    private boolean checkMailIsHappy(Mail mail) {
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailIsGloomy(Mail mail) {
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail
        log.info("Sending happy mail to {}", mail.getMailList());
        // Here template/message for happy mail would be applied and sent
        mail.setStatus("SENT_HAPPY");
        log.info("Happy mail sent for Mail ID: {}", mail.getId());
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending gloomy mail to {}", mail.getMailList());
        // Here template/message for gloomy mail would be applied and sent
        mail.setStatus("SENT_GLOOMY");
        log.info("Gloomy mail sent for Mail ID: {}", mail.getId());
    }
}