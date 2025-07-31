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
    public ResponseEntity<String> createMail(@RequestBody Mail mail) {
        if (!mail.isValid()) {
            log.error("Invalid Mail entity received");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);
        processMail(technicalId, mail);
        return new ResponseEntity<>(technicalId, HttpStatus.CREATED);
    }

    @GetMapping("/mail/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail entity with id {} not found", id);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        log.info("Mail entity retrieved with id: {}", id);
        return new ResponseEntity<>(mail, HttpStatus.OK);
    }

    private void processMail(String technicalId, Mail mail) {
        // Business logic implementation:
        // 1. Validate mailList content format (e.g., comma-separated emails)
        // 2. Check criteria for happy or gloomy mail based on isHappy flag
        // 3. If happy, send happy mail using sendHappyMail processor
        // 4. If gloomy, send gloomy mail using sendGloomyMail processor

        log.info("Processing Mail entity with id: {}", technicalId);

        if (!mail.isValid()) {
            log.error("Mail entity with id {} failed validation during processing", technicalId);
            return;
        }

        boolean isHappy = mail.isHappy();
        String mailList = mail.getMailList();

        // Example criteria checks (simple boolean check)
        if (isHappy) {
            sendHappyMail(technicalId, mailList);
        } else {
            sendGloomyMail(technicalId, mailList);
        }
    }

    private void sendHappyMail(String technicalId, String mailList) {
        // Implement sending happy mail logic here
        log.info("Sending happy mail for technicalId {} to recipients: {}", technicalId, mailList);
        // Real implementation would send the actual mails via SMTP or external mail service API
    }

    private void sendGloomyMail(String technicalId, String mailList) {
        // Implement sending gloomy mail logic here
        log.info("Sending gloomy mail for technicalId {} to recipients: {}", technicalId, mailList);
        // Real implementation would send the actual mails via SMTP or external mail service API
    }
}