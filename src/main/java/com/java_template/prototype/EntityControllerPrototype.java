package com.java_template.prototype;

import com.java_template.application.entity.Mail;
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

    // POST /prototype/mail - create a new Mail entity
    @PostMapping("/mail")
    public ResponseEntity<String> createMail(@RequestBody Mail mail) {
        if (!mail.isValid()) {
            log.error("Invalid Mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with ID: {}", technicalId);

        processMail(technicalId, mail);

        return ResponseEntity.status(HttpStatus.CREATED).body(technicalId);
    }

    // GET /prototype/mail/{id} - retrieve a Mail entity by technical ID
    @GetMapping("/mail/{id}")
    public ResponseEntity<Mail> getMail(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail entity not found for ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        log.info("Mail entity retrieved for ID: {}", id);
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        // Implement business logic for processing Mail entity

        // Example: Validate important fields
        if (mail.getRecipient() == null || mail.getRecipient().isBlank()) {
            log.error("Mail recipient is blank for ID: {}", technicalId);
            return;
        }

        if (mail.getSubject() == null || mail.getSubject().isBlank()) {
            log.info("Mail subject is blank for ID: {}", technicalId);
        }

        // Example: Simulate sending email notification
        log.info("Sending email to {} with subject: {}", mail.getRecipient(), mail.getSubject());

        // Additional business logic can be added here

        log.info("Processing of Mail entity completed for ID: {}", technicalId);
    }

}
