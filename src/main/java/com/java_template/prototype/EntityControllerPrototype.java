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
        if (!mail.isValid()) {
            log.error("Invalid mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail created with technicalId {}", technicalId);

        processMail(technicalId, mail);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail not found for technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing mail with technicalId {}", technicalId);

        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            if (checkMailHappy(mail)) {
                sendHappyMail(technicalId, mail);
            } else {
                log.error("Mail failed happy criteria check: {}", technicalId);
            }
        } else {
            if (checkMailGloomy(mail)) {
                sendGloomyMail(technicalId, mail);
            } else {
                log.error("Mail failed gloomy criteria check: {}", technicalId);
            }
        }
    }

    private boolean checkMailHappy(Mail mail) {
        // Criteria: mail isHappy == true
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailGloomy(Mail mail) {
        // Criteria: mail isHappy == false
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        // Simulate sending happy mail
        log.info("Sending happy mail to {} with subject '{}'", mail.getMailList(), mail.getSubject());
        // Here could be integration with JavaMailSender or external mail API
        // For prototype, we just log success
        log.info("Happy mail sent successfully for technicalId {}", technicalId);
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending gloomy mail to {} with subject '{}'", mail.getMailList(), mail.getSubject());
        // For prototype, we just log success
        log.info("Gloomy mail sent successfully for technicalId {}", technicalId);
    }
}