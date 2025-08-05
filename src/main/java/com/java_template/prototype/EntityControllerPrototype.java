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
        log.info("Mail created with technicalId={}", technicalId);

        processMail(technicalId, mail);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found for technicalId={}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Mail retrieved for technicalId={}", technicalId);
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail with technicalId={}", technicalId);

        // Example criteria checks (could be extended)
        if (mail.getIsHappy()) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        // Implement sending happy mail logic here
        log.info("Sending Happy Mail to recipients: {}", mail.getMailList());
        // Simulate sending mails
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        // Implement sending gloomy mail logic here
        log.info("Sending Gloomy Mail to recipients: {}", mail.getMailList());
        // Simulate sending mails
    }
}