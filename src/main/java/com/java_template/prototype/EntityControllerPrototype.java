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
        if (mail == null || mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Invalid mail creation request: mail or mailList is null/empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        // Generate technicalId as a string from atomic counter
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail created with technicalId {}", technicalId);

        // Trigger processing for the newly created mail entity
        processMail(technicalId, mail);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Mail retrieved with technicalId {}", technicalId);
        return ResponseEntity.ok(mail);
    }

    @GetMapping("/mail")
    public ResponseEntity<List<Mail>> getMailsByIsHappy(@RequestParam(required = false) Boolean isHappy) {
        if (isHappy == null) {
            // Return all mails if no filter is provided
            List<Mail> allMails = new ArrayList<>(mailCache.values());
            log.info("Retrieving all mails, count {}", allMails.size());
            return ResponseEntity.ok(allMails);
        }
        // Filter mails by isHappy flag
        List<Mail> filteredMails = new ArrayList<>();
        for (Mail mail : mailCache.values()) {
            if (isHappy.equals(mail.getIsHappy())) {
                filteredMails.add(mail);
            }
        }
        log.info("Retrieving mails filtered by isHappy={}, count {}", isHappy, filteredMails.size());
        return ResponseEntity.ok(filteredMails);
    }

    private void processMail(String technicalId, Mail mail) {
        // Validation phase - could invoke criteria checks if implemented
        boolean isHappy = Boolean.TRUE.equals(mail.getIsHappy());

        // Business logic for happy mails
        if (isHappy) {
            boolean happySent = sendHappyMail(mail.getMailList());
            if (happySent) {
                log.info("Happy mail sent successfully for technicalId {}", technicalId);
            } else {
                log.error("Failed to send happy mail for technicalId {}", technicalId);
            }
        }
        // Business logic for gloomy mails
        else {
            boolean gloomySent = sendGloomyMail(mail.getMailList());
            if (gloomySent) {
                log.info("Gloomy mail sent successfully for technicalId {}", technicalId);
            } else {
                log.error("Failed to send gloomy mail for technicalId {}", technicalId);
            }
        }
        // Completion and notification could be expanded here if needed
    }

    private boolean sendHappyMail(List<String> mailList) {
        // Simulate sending happy mail to recipients
        try {
            for (String recipient : mailList) {
                log.info("Sending happy mail to {}", recipient);
                // Here, integrate with actual mail sending service
            }
            return true;
        } catch (Exception e) {
            log.error("Exception while sending happy mail: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendGloomyMail(List<String> mailList) {
        // Simulate sending gloomy mail to recipients
        try {
            for (String recipient : mailList) {
                log.info("Sending gloomy mail to {}", recipient);
                // Here, integrate with actual mail sending service
            }
            return true;
        } catch (Exception e) {
            log.error("Exception while sending gloomy mail: {}", e.getMessage());
            return false;
        }
    }
}