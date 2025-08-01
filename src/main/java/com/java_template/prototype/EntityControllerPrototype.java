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
            log.error("Invalid Mail entity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Mail entity");
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);
        processMail(technicalId, mail);
        return ResponseEntity.status(HttpStatus.CREATED).body(technicalId);
    }

    @GetMapping("/mail/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail entity with id {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        // Business logic for processing Mail entity
        if (mail.getIsHappy()) {
            sendHappyMail(mail);
        } else {
            sendGloomyMail(mail);
        }
        log.info("Processed Mail entity with technicalId: {}", technicalId);
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail by logging
        for (String recipient : mail.getMailList()) {
            log.info("Sending happy mail to: {}", recipient);
        }
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail by logging
        for (String recipient : mail.getMailList()) {
            log.info("Sending gloomy mail to: {}", recipient);
        }
    }
}