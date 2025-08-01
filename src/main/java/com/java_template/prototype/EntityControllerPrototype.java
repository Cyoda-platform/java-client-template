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

        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);

        processMail(technicalId, mail);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail entity not found for technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        // Validation: mail.isValid() already checked in controller
        log.info("Processing Mail entity with technicalId: {}", technicalId);

        // Check criteria and call processors
        if (mail.isHappy()) {
            processMailSendHappyMail(technicalId, mail);
        } else {
            processMailSendGloomyMail(technicalId, mail);
        }
    }

    private void processMailSendHappyMail(String technicalId, Mail mail) {
        log.info("Sending Happy Mail for technicalId: {}", technicalId);
        for (String recipient : mail.getMailList()) {
            // Simulate sending happy mail
            log.info("Happy mail sent to: {} with subject: {}", recipient, mail.getSubject());
        }
        log.info("Completed sending Happy Mail for technicalId: {}", technicalId);
    }

    private void processMailSendGloomyMail(String technicalId, Mail mail) {
        log.info("Sending Gloomy Mail for technicalId: {}", technicalId);
        for (String recipient : mail.getMailList()) {
            // Simulate sending gloomy mail
            log.info("Gloomy mail sent to: {} with subject: {}", recipient, mail.getSubject());
        }
        log.info("Completed sending Gloomy Mail for technicalId: {}", technicalId);
    }
}