package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    @PostMapping("/mails")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        if (mail == null || !mail.isValid()) {
            log.error("Invalid Mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String technicalId = "mail-" + mailIdCounter.getAndIncrement();
        mailCache.put(technicalId, mail);

        // Trigger processing
        processMail(technicalId, mail);

        log.info("Mail created with technicalId: {}", technicalId);
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<MailStatusResponse> getMail(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MailStatusResponse response = new MailStatusResponse();
        response.setTechnicalId(technicalId);
        response.setIsHappy(mail.getIsHappy());
        response.setMailList(mail.getMailList());
        response.setStatus("COMPLETED"); // For prototype, assume completed after processing

        log.info("Mail retrieved with technicalId: {}", technicalId);
        return ResponseEntity.ok(response);
    }

    // Business logic for processing Mail entity
    private void processMail(String technicalId, Mail mail) {
        // Validation already done in isValid()
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(mail.getMailList());
            log.info("Processed sendHappyMail for technicalId: {}", technicalId);
        } else {
            sendGloomyMail(mail.getMailList());
            log.info("Processed sendGloomyMail for technicalId: {}", technicalId);
        }
        // Completion and notification logic can be added here
    }

    private void sendHappyMail(List<String> recipients) {
        // Simulate sending happy mails
        for (String recipient : recipients) {
            log.info("Sending HAPPY mail to {}", recipient);
        }
    }

    private void sendGloomyMail(List<String> recipients) {
        // Simulate sending gloomy mails
        for (String recipient : recipients) {
            log.info("Sending GLOOMY mail to {}", recipient);
        }
    }

    // Response DTO for GET /mails/{technicalId}
    public static class MailStatusResponse {
        private String technicalId;
        private Boolean isHappy;
        private List<String> mailList;
        private String status;

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }

        public Boolean getIsHappy() {
            return isHappy;
        }

        public void setIsHappy(Boolean isHappy) {
            this.isHappy = isHappy;
        }

        public List<String> getMailList() {
            return mailList;
        }

        public void setMailList(List<String> mailList) {
            this.mailList = mailList;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}