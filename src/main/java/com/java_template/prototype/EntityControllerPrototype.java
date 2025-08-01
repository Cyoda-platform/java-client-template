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
            log.error("Invalid mail data: mailList is null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList cannot be null or empty"));
        }
        if (!mail.isValid()) {
            log.error("Invalid mail entity validation failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Mail entity validation failed"));
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);

        try {
            processMail(technicalId, mail);
        } catch (Exception e) {
            log.error("Error processing mail with technicalId {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Processing failed"));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<MailResponse> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        MailResponse response = new MailResponse(technicalId, mail.isHappy(), mail.getMailList());
        return ResponseEntity.ok(response);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing mail with technicalId: {}", technicalId);

        // Validate mail list email addresses
        List<String> validEmails = new ArrayList<>();
        for (String email : mail.getMailList()) {
            if (email != null && !email.isBlank() && email.contains("@")) {
                validEmails.add(email);
            } else {
                log.warn("Invalid email skipped: {}", email);
            }
        }

        if (validEmails.isEmpty()) {
            log.error("No valid email addresses found for mail with technicalId: {}", technicalId);
            return;
        }

        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(validEmails, technicalId);
        } else {
            sendGloomyMail(validEmails, technicalId);
        }

        log.info("Completed processing mail with technicalId: {}", technicalId);
    }

    private void sendHappyMail(List<String> mailList, String technicalId) {
        // Simulate sending happy mails
        log.info("Sending HAPPY mails to {} recipients for mailId: {}", mailList.size(), technicalId);
        for (String email : mailList) {
            log.info("Happy mail sent to: {}", email);
        }
    }

    private void sendGloomyMail(List<String> mailList, String technicalId) {
        // Simulate sending gloomy mails
        log.info("Sending GLOOMY mails to {} recipients for mailId: {}", mailList.size(), technicalId);
        for (String email : mailList) {
            log.info("Gloomy mail sent to: {}", email);
        }
    }

    // Inner static class for GET response to avoid exposing entity directly
    static class MailResponse {
        private final String technicalId;
        private final Boolean isHappy;
        private final List<String> mailList;

        public MailResponse(String technicalId, Boolean isHappy, List<String> mailList) {
            this.technicalId = technicalId;
            this.isHappy = isHappy;
            this.mailList = mailList;
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
    }
}