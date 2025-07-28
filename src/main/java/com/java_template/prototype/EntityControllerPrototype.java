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
        if (mail == null) {
            log.error("Received null Mail object");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Mail object is required"));
        }
        if (!mail.isValid()) {
            log.error("Invalid Mail entity: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Mail entity: mailList, content, and isHappy are required"));
        }

        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mail.setStatus("CREATED");
        mailCache.put(technicalId, mail);

        try {
            processMail(technicalId, mail);
            log.info("Successfully processed Mail with ID {}", technicalId);
        } catch (Exception e) {
            log.error("Failed to process Mail with ID {}: {}", technicalId, e.getMessage());
            mail.setStatus("FAILED");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail with ID {} not found", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        }
        return ResponseEntity.ok(mail);
    }

    @GetMapping("/mail")
    public ResponseEntity<List<Mail>> getMailByCondition(@RequestParam(required = false) Boolean isHappy) {
        List<Mail> result = new ArrayList<>();
        for (Mail mail : mailCache.values()) {
            if (isHappy == null || (mail.getIsHappy() != null && mail.getIsHappy().equals(isHappy))) {
                result.add(mail);
            }
        }
        return ResponseEntity.ok(result);
    }

    private void processMail(String technicalId, Mail mail) {
        // Validate mail fields again for safety
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail ID {} has empty mailList", technicalId);
            mail.setStatus("FAILED");
            return;
        }

        if (mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Mail ID {} has empty content", technicalId);
            mail.setStatus("FAILED");
            return;
        }

        if (mail.getIsHappy() == null) {
            log.error("Mail ID {} has null isHappy field", technicalId);
            mail.setStatus("FAILED");
            return;
        }

        try {
            // Criteria check and processing
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                // sendHappyMail processor
                sendHappyMail(technicalId, mail);
                mail.setStatus("SENT_HAPPY");
                log.info("Mail ID {} sent as happy mail", technicalId);
            } else {
                // sendGloomyMail processor
                sendGloomyMail(technicalId, mail);
                mail.setStatus("SENT_GLOOMY");
                log.info("Mail ID {} sent as gloomy mail", technicalId);
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            log.error("Error processing Mail ID {}: {}", technicalId, e.getMessage());
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        // Simulate sending happy mail
        // In real implementation, integrate with JavaMailSender or other mailing API
        log.info("Sending happy mail to recipients: {}", mail.getMailList());
        // Example: for each recipient send mail with mail.getContent()
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        // Simulate sending gloomy mail
        // In real implementation, integrate with JavaMailSender or other mailing API
        log.info("Sending gloomy mail to recipients: {}", mail.getMailList());
        // Example: for each recipient send mail with mail.getContent()
    }
}