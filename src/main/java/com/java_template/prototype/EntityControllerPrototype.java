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
        if (mail == null || !mail.isValid()) {
            log.error("Invalid Mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Generate technicalId as string of incremented long value
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());

        // Save entity to cache
        mailCache.put(technicalId, mail);

        log.info("Mail entity created with technicalId={}", technicalId);

        // Trigger processing
        processMail(technicalId, mail);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<Mail> getMail(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found for technicalId={}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Mail entity retrieved for technicalId={}", technicalId);
        return ResponseEntity.ok(mail);
    }

    // Business logic for processing Mail entity according to requirements
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail entity technicalId={}", technicalId);

        // Step 1: Validate mailList is non-empty and email format basic check
        List<String> mailList = mail.getMailList();
        if (mailList == null || mailList.isEmpty()) {
            log.error("Mail list empty or null for technicalId={}", technicalId);
            return;
        }
        boolean invalidEmailFound = mailList.stream().anyMatch(email -> email == null || email.isBlank() || !email.contains("@"));
        if (invalidEmailFound) {
            log.error("Invalid email address found in mailList for technicalId={}", technicalId);
            return;
        }

        // Step 2: Determine mail type and send appropriate mails
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(mailList, technicalId);
        } else {
            sendGloomyMail(mailList, technicalId);
        }

        // Step 3: Log completion
        log.info("Finished processing Mail entity technicalId={}", technicalId);
    }

    private void sendHappyMail(List<String> recipients, String technicalId) {
        // Simulate sending happy mail
        for (String recipient : recipients) {
            log.info("Sent HAPPY mail to {} for technicalId={}", recipient, technicalId);
        }
    }

    private void sendGloomyMail(List<String> recipients, String technicalId) {
        // Simulate sending gloomy mail
        for (String recipient : recipients) {
            log.info("Sent GLOOMY mail to {} for technicalId={}", recipient, technicalId);
        }
    }
}