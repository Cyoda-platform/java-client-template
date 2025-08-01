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
            log.error("Invalid mail entity received");
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

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<MailResponse> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        MailResponse response = new MailResponse(technicalId, mail, "COMPLETED");
        return ResponseEntity.ok(response);
    }

    private void processMail(String technicalId, Mail mail) {
        // Business logic for processing mail entity
        try {
            log.info("Processing mail with technicalId: {}", technicalId);

            // Validate mail list and content already done in isValid()

            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(mail);
                log.info("Happy mail sent for technicalId: {}", technicalId);
            } else {
                sendGloomyMail(mail);
                log.info("Gloomy mail sent for technicalId: {}", technicalId);
            }

            // Here you might update status or notify system, but we keep immutable data principle

        } catch (Exception e) {
            log.error("Error processing mail with technicalId: {}", technicalId, e);
        }
    }

    private void sendHappyMail(Mail mail) {
        // Implement actual sending logic for happy mails
        // For prototype, we simulate sending with logs
        log.info("Sending HAPPY mail to recipients: {}", mail.getMailList());
        log.info("Subject: {}", mail.getSubject());
        log.info("Content: {}", mail.getContent());
    }

    private void sendGloomyMail(Mail mail) {
        // Implement actual sending logic for gloomy mails
        // For prototype, we simulate sending with logs
        log.info("Sending GLOOMY mail to recipients: {}", mail.getMailList());
        log.info("Subject: {}", mail.getSubject());
        log.info("Content: {}", mail.getContent());
    }

    // Response DTO for GET /mail/{technicalId}
    private static class MailResponse {
        private final String technicalId;
        private final Boolean isHappy;
        private final List<String> mailList;
        private final String subject;
        private final String content;
        private final String status;

        public MailResponse(String technicalId, Mail mail, String status) {
            this.technicalId = technicalId;
            this.isHappy = mail.getIsHappy();
            this.mailList = mail.getMailList();
            this.subject = mail.getSubject();
            this.content = mail.getContent();
            this.status = status;
        }

        public String getTechnicalId() {
            return technicalId;
        }

        public Boolean getIsHappy() {
            return isHappy;
        }

        public List<String> getMailList() {
            return mailList;
        }

        public String getSubject() {
            return subject;
        }

        public String getContent() {
            return content;
        }

        public String getStatus() {
            return status;
        }
    }
}