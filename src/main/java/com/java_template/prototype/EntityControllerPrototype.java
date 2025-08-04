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

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<Map<String, Object>> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Map<String, Object> response = new HashMap<>();
        response.put("technicalId", technicalId);
        response.put("isHappy", mail.getIsHappy());
        response.put("mailList", mail.getMailList());
        response.put("status", "COMPLETED"); // For prototype, mark as COMPLETED
        log.info("Mail entity retrieved with technicalId: {}", technicalId);
        return ResponseEntity.ok(response);
    }

    private void processMail(String technicalId, Mail mail) {
        try {
            if (mail.getIsHappy()) {
                sendHappyMail(mail);
                log.info("Processed happy mail for technicalId: {}", technicalId);
            } else {
                sendGloomyMail(mail);
                log.info("Processed gloomy mail for technicalId: {}", technicalId);
            }
            // Mark completion or send event notification here if needed
        } catch (Exception e) {
            log.error("Error processing mail with technicalId: {}", technicalId, e);
        }
    }

    private void sendHappyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            log.info("Sending happy mail to {}", recipient);
            // Simulate sending happy mail - in real app, call JavaMailSender
            // subject: "Happy News!"
            // body: "Wishing you a joyful day!"
        }
    }

    private void sendGloomyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            log.info("Sending gloomy mail to {}", recipient);
            // Simulate sending gloomy mail - in real app, call JavaMailSender
            // subject: "Gloomy News"
            // body: "We hope things get better soon."
        }
    }
}