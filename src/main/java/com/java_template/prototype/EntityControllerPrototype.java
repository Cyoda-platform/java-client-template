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
            log.error("Invalid mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mail.setStatus("PENDING");
        mailCache.put(technicalId, mail);

        log.info("Mail created with technicalId {}", technicalId);

        processMail(technicalId, mail);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    @GetMapping("/mails")
    public ResponseEntity<List<Mail>> getMailsByIsHappy(@RequestParam Optional<Boolean> isHappy) {
        if (isHappy.isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>(mailCache.values()));
        }
        List<Mail> filtered = new ArrayList<>();
        for (Mail mail : mailCache.values()) {
            if (mail.isHappy() == isHappy.get()) {
                filtered.add(mail);
            }
        }
        return ResponseEntity.ok(filtered);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing mail with technicalId {}", technicalId);

        // Validation criteria
        boolean happyCriteria = checkMailHappy(mail);
        boolean gloomyCriteria = checkMailGloomy(mail);

        try {
            if (happyCriteria) {
                sendHappyMail(mail);
                mail.setStatus("SENT");
                log.info("Happy mail sent to recipients: {}", mail.getMailList());
            } else if (gloomyCriteria) {
                sendGloomyMail(mail);
                mail.setStatus("SENT");
                log.info("Gloomy mail sent to recipients: {}", mail.getMailList());
            } else {
                mail.setStatus("FAILED");
                log.error("Mail does not meet happy or gloomy criteria");
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            log.error("Error sending mail: {}", e.getMessage());
        }

        // Update cache with new status
        mailCache.put(technicalId, mail);
    }

    private boolean checkMailHappy(Mail mail) {
        return mail.isHappy();
    }

    private boolean checkMailGloomy(Mail mail) {
        return !mail.isHappy();
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail
        log.info("Sending happy mail: {}", mail.getContent());
        // Here you would implement actual email sending logic using JavaMailSender or equivalent
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending gloomy mail: {}", mail.getContent());
        // Here you would implement actual email sending logic using JavaMailSender or equivalent
    }
}