package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    // Simulated mail sending service for prototype purposes
    private final MailSender mailSender = new MailSender();

    @PostMapping("/mails")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        if (mail == null || mail.getIsHappy() == null || mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Invalid mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity saved with technicalId {}", technicalId);

        try {
            processMail(technicalId, mail);
        } catch (Exception e) {
            log.error("Error processing mail with technicalId {}: {}", technicalId, e.getMessage());
            // Continue, as per EDA principles
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<MailResponse> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        MailResponse response = new MailResponse(mail.getIsHappy(), mail.getMailList(), mail.getStatus());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/mails")
    public ResponseEntity<List<MailResponseWithId>> getMailsByIsHappy(@RequestParam(required = false) Boolean isHappy) {
        if (isHappy == null) {
            return ResponseEntity.badRequest().build();
        }
        List<MailResponseWithId> result = new ArrayList<>();
        for (Map.Entry<String, Mail> entry : mailCache.entrySet()) {
            Mail mail = entry.getValue();
            if (mail.getIsHappy() != null && mail.getIsHappy().equals(isHappy)) {
                result.add(new MailResponseWithId(entry.getKey(), mail.getIsHappy(), mail.getMailList()));
            }
        }
        return ResponseEntity.ok(result);
    }

    private void processMail(String technicalId, Mail mail) {
        // Validate mailList not empty and emails not blank
        if (mail.getMailList().isEmpty()) {
            log.error("Mail {} has empty mailList", technicalId);
            mail.setStatus("FAILED");
            return;
        }
        for (String email : mail.getMailList()) {
            if (email == null || email.isBlank()) {
                log.error("Mail {} has invalid email in mailList", technicalId);
                mail.setStatus("FAILED");
                return;
            }
        }
        // Process according to isHappy flag
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(mail.getMailList(), technicalId);
        } else {
            sendGloomyMail(mail.getMailList(), technicalId);
        }
    }

    private void sendHappyMail(List<String> mailList, String technicalId) {
        String subject = "Happy Greetings!";
        String content = "Wishing you a joyful and happy day!";
        boolean success = mailSender.sendEmails(mailList, subject, content);
        mailCache.get(technicalId).setStatus(success ? "SENT" : "FAILED");
        if (success) {
            log.info("Happy mail sent successfully for technicalId {}", technicalId);
        } else {
            log.error("Failed to send happy mail for technicalId {}", technicalId);
        }
    }

    private void sendGloomyMail(List<String> mailList, String technicalId) {
        String subject = "Thoughtful Reflections";
        String content = "Sometimes, a quiet moment is needed to reflect.";
        boolean success = mailSender.sendEmails(mailList, subject, content);
        mailCache.get(technicalId).setStatus(success ? "SENT" : "FAILED");
        if (success) {
            log.info("Gloomy mail sent successfully for technicalId {}", technicalId);
        } else {
            log.error("Failed to send gloomy mail for technicalId {}", technicalId);
        }
    }

    // Inner class to simulate mail sending
    static class MailSender {
        boolean sendEmails(List<String> recipients, String subject, String content) {
            try {
                // Simulate sending emails
                for (String recipient : recipients) {
                    // Simulate sending email logic here; in real app use JavaMailSender
                    // log.info("Sending mail to {}: Subject: {}, Content: {}", recipient, subject, content);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    // DTO for GET /mails/{id} response
    static class MailResponse {
        private Boolean isHappy;
        private List<String> mailList;
        private String status;

        public MailResponse(Boolean isHappy, List<String> mailList, String status) {
            this.isHappy = isHappy;
            this.mailList = mailList;
            this.status = status;
        }

        public Boolean getIsHappy() {
            return isHappy;
        }

        public List<String> getMailList() {
            return mailList;
        }

        public String getStatus() {
            return status;
        }
    }

    // DTO for GET /mails?isHappy=true response
    static class MailResponseWithId {
        private String technicalId;
        private Boolean isHappy;
        private List<String> mailList;

        public MailResponseWithId(String technicalId, Boolean isHappy, List<String> mailList) {
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