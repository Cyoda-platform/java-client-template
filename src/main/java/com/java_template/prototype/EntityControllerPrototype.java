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
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mailRequest) {
        if (mailRequest == null || mailRequest.getMailList() == null || mailRequest.getMailList().isEmpty()) {
            log.error("Invalid mail creation request: mailList is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList is required and cannot be empty"));
        }

        // Generate technicalId as string of incremented long
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());

        // Initialize mail entity
        Mail mail = new Mail();
        mail.setMailList(mailRequest.getMailList());
        mail.setIsHappy(null); // will be set by criteria processing
        mail.setStatus("CREATED");

        // Save to cache
        mailCache.put(technicalId, mail);

        // Process mail event-driven logic
        processMail(technicalId, mail);

        // Return only technicalId
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        }
        return ResponseEntity.ok(mail);
    }

    // Process method implementing business logic as per requirements
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail with ID: {}", technicalId);

        // Step 2: Validation criteria
        boolean happyCriteriaMet = checkMailHappyCriteria(mail);
        boolean gloomyCriteriaMet = checkMailGloomyCriteria(mail);

        // Step 3: Classification based on criteria
        if (happyCriteriaMet) {
            mail.setIsHappy(true);
            mail.setStatus("PROCESSING");
            log.info("Mail ID {} classified as Happy", technicalId);
            sendHappyMail(technicalId, mail);
        } else if (gloomyCriteriaMet) {
            mail.setIsHappy(false);
            mail.setStatus("PROCESSING");
            log.info("Mail ID {} classified as Gloomy", technicalId);
            sendGloomyMail(technicalId, mail);
        } else {
            // If criteria not met, default to gloomy (or could reject)
            mail.setIsHappy(false);
            mail.setStatus("PROCESSING");
            log.info("Mail ID {} did not meet happy criteria, classified as Gloomy by default", technicalId);
            sendGloomyMail(technicalId, mail);
        }

        // Update cache with changed mail state
        mailCache.put(technicalId, mail);
    }

    // Criteria checks - add real logic as needed
    private boolean checkMailHappyCriteria(Mail mail) {
        // Example: happy if mailList size is even (placeholder logic)
        return mail.getMailList().size() % 2 == 0;
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // Example: gloomy if mailList size is odd (placeholder logic)
        return mail.getMailList().size() % 2 != 0;
    }

    // Processor for happy mail
    private void sendHappyMail(String technicalId, Mail mail) {
        try {
            // Simulate sending happy mail with different template/content
            log.info("Sending Happy Mail to: {}", mail.getMailList());
            // Simulate success
            mail.setStatus("SENT");
            log.info("Happy Mail sent successfully for ID: {}", technicalId);
        } catch (Exception e) {
            mail.setStatus("FAILED");
            log.error("Failed to send Happy Mail for ID: {}, error: {}", technicalId, e.getMessage());
        }
        mailCache.put(technicalId, mail);
    }

    // Processor for gloomy mail
    private void sendGloomyMail(String technicalId, Mail mail) {
        try {
            // Simulate sending gloomy mail with different template/content
            log.info("Sending Gloomy Mail to: {}", mail.getMailList());
            // Simulate success
            mail.setStatus("SENT");
            log.info("Gloomy Mail sent successfully for ID: {}", technicalId);
        } catch (Exception e) {
            mail.setStatus("FAILED");
            log.error("Failed to send Gloomy Mail for ID: {}, error: {}", technicalId, e.getMessage());
        }
        mailCache.put(technicalId, mail);
    }
}