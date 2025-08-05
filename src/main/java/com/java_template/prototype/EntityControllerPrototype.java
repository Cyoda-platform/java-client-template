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

        long id = mailIdCounter.getAndIncrement();
        String technicalId = String.valueOf(id);
        mail.setStatus("PENDING");
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
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail entity with technicalId: {}", technicalId);

        // Validate mail fields (already partially validated in isValid)
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("MailList is empty for Mail with technicalId: {}", technicalId);
            mail.setStatus("FAILED");
            mailCache.put(technicalId, mail);
            return;
        }
        if (mail.getSubject() == null || mail.getSubject().isBlank()) {
            log.error("Subject is blank for Mail with technicalId: {}", technicalId);
            mail.setStatus("FAILED");
            mailCache.put(technicalId, mail);
            return;
        }
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Content is blank for Mail with technicalId: {}", technicalId);
            mail.setStatus("FAILED");
            mailCache.put(technicalId, mail);
            return;
        }

        try {
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                if (checkMailHappy(mail)) {
                    sendHappyMail(mail);
                    mail.setStatus("SENT");
                } else {
                    log.error("Mail did not pass happy criteria for technicalId: {}", technicalId);
                    mail.setStatus("FAILED");
                }
            } else {
                if (checkMailGloomy(mail)) {
                    sendGloomyMail(mail);
                    mail.setStatus("SENT");
                } else {
                    log.error("Mail did not pass gloomy criteria for technicalId: {}", technicalId);
                    mail.setStatus("FAILED");
                }
            }
        } catch (Exception e) {
            log.error("Exception during mail processing for technicalId: {}: {}", technicalId, e.getMessage());
            mail.setStatus("FAILED");
        }

        mailCache.put(technicalId, mail);
        log.info("Processing completed for Mail with technicalId: {} with status: {}", technicalId, mail.getStatus());
    }

    private boolean checkMailHappy(Mail mail) {
        // Criteria: isHappy == true
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailGloomy(Mail mail) {
        // Criteria: isHappy == false
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail
        log.info("Sending Happy Mail to recipients: {}", mail.getMailList());
        // Here could be integration with SMTP or external mail service
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending Gloomy Mail to recipients: {}", mail.getMailList());
        // Here could be integration with SMTP or external mail service
    }
}