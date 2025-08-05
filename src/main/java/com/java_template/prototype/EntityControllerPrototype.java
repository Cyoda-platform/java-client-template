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

    @PostMapping("/mail")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        if (!mail.isValid()) {
            log.error("Invalid Mail entity received");
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
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Mail entity retrieved for technicalId: {}", technicalId);
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        // Validation
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("MailList is empty for mail with technicalId: {}", technicalId);
            return;
        }
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Mail content is blank for mail with technicalId: {}", technicalId);
            return;
        }

        // Criteria Evaluation and Routing
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            processMailSendHappy(technicalId, mail);
        } else {
            processMailSendGloomy(technicalId, mail);
        }
    }

    private void processMailSendHappy(String technicalId, Mail mail) {
        // Simulate sending happy mails
        for (String recipient : mail.getMailList()) {
            log.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
            // Implement actual mail sending logic here if needed
        }
        log.info("All happy mails sent successfully for mail with technicalId: {}", technicalId);
    }

    private void processMailSendGloomy(String technicalId, Mail mail) {
        // Simulate sending gloomy mails
        for (String recipient : mail.getMailList()) {
            log.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
            // Implement actual mail sending logic here if needed
        }
        log.info("All gloomy mails sent successfully for mail with technicalId: {}", technicalId);
    }
}