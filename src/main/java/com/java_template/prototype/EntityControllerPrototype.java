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
        log.info("Received request to create Mail");

        if (mail == null) {
            log.error("Mail entity is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Mail entity is required"));
        }
        if (!mail.isValid()) {
            log.error("Mail entity validation failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Mail entity fields"));
        }

        String id = String.valueOf(mailIdCounter.getAndIncrement());
        mail.setStatus("PENDING");
        mailCache.put(id, mail);

        try {
            processMail(mail);
        } catch (Exception e) {
            log.error("Error processing Mail with ID {}: {}", id, e.getMessage());
            mail.setStatus("FAILED");
        }

        log.info("Mail created with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    @GetMapping("/mail/{id}")
    public ResponseEntity<?> getMailById(@PathVariable String id) {
        log.info("Received request to get Mail with ID: {}", id);
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        }
        return ResponseEntity.ok(mail);
    }

    // Optional: Filtering by isHappy parameter
    @GetMapping(value = "/mail", params = "isHappy")
    public ResponseEntity<List<Mail>> getMailsByIsHappy(@RequestParam boolean isHappy) {
        log.info("Received request to get Mails filtered by isHappy={}", isHappy);
        List<Mail> filteredMails = new ArrayList<>();
        for (Map.Entry<String, Mail> entry : mailCache.entrySet()) {
            Mail mail = entry.getValue();
            if (mail.getIsHappy() != null && mail.getIsHappy() == isHappy) {
                filteredMails.add(mail);
            }
        }
        return ResponseEntity.ok(filteredMails);
    }

    private void processMail(Mail mail) {
        log.info("Processing Mail with current status: {}", mail.getStatus());

        // Step 1: Validate criteria
        boolean isHappyCriteria = checkMailIsHappy(mail);
        boolean isGloomyCriteria = checkMailIsGloomy(mail);

        // Step 2: Process according to criteria
        if (isHappyCriteria) {
            sendHappyMail(mail);
        } else if (isGloomyCriteria) {
            sendGloomyMail(mail);
        } else {
            log.error("Mail does not meet happy or gloomy criteria");
            mail.setStatus("FAILED");
            return;
        }

        // Step 3: Update status to SENT
        mail.setStatus("SENT");
        log.info("Mail processed and sent successfully");
    }

    private boolean checkMailIsHappy(Mail mail) {
        // Criteria: isHappy == true
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailIsGloomy(Mail mail) {
        // Criteria: isHappy == false
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(Mail mail) {
        log.info("Sending happy mail to recipients: {}", mail.getMailList());
        // Simulate sending happy mail content here
        // Real implementation would send emails or trigger external service
    }

    private void sendGloomyMail(Mail mail) {
        log.info("Sending gloomy mail to recipients: {}", mail.getMailList());
        // Simulate sending gloomy mail content here
        // Real implementation would send emails or trigger external service
    }
}