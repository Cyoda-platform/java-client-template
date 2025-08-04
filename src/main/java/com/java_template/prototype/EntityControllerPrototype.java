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
            log.error("Invalid Mail entity received for creation");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        // Generate technicalId as a string
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mail.setStatus("PENDING");
        mailCache.put(technicalId, mail);

        try {
            processMail(technicalId, mail);
            log.info("Mail processed successfully with technicalId: {}", technicalId);
        } catch (Exception e) {
            log.error("Processing Mail failed for technicalId: {}", technicalId, e);
            mail.setStatus("FAILED");
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    @GetMapping(value = "/mails", params = "isHappy")
    public ResponseEntity<List<Mail>> getMailsByIsHappy(@RequestParam boolean isHappy) {
        List<Mail> filteredMails = new ArrayList<>();
        for (Mail mail : mailCache.values()) {
            if (mail.getIsHappy() != null && mail.getIsHappy() == isHappy) {
                filteredMails.add(mail);
            }
        }
        return ResponseEntity.ok(filteredMails);
    }

    private void processMail(String technicalId, Mail mail) {
        // Step 1: Validate mailList, subject, content already done via isValid()

        // Step 2: Classification criteria (simple example: if subject or content contains "happy" ignore case)
        boolean happyCriteria = checkMailHappyCriteria(mail);
        boolean gloomyCriteria = checkMailGloomyCriteria(mail);

        if (happyCriteria) {
            mail.setIsHappy(true);
            sendHappyMail(mail);
        } else if (gloomyCriteria) {
            mail.setIsHappy(false);
            sendGloomyMail(mail);
        } else {
            // Default to gloomy if neither criteria matches
            mail.setIsHappy(false);
            sendGloomyMail(mail);
        }

        // Update status after sending
        mail.setStatus("SENT");
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        String subject = mail.getSubject() != null ? mail.getSubject().toLowerCase() : "";
        String content = mail.getContent() != null ? mail.getContent().toLowerCase() : "";
        return subject.contains("happy") || content.contains("happy");
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        String subject = mail.getSubject() != null ? mail.getSubject().toLowerCase() : "";
        String content = mail.getContent() != null ? mail.getContent().toLowerCase() : "";
        return subject.contains("sad") || content.contains("sad") || subject.contains("gloomy") || content.contains("gloomy");
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail
        log.info("Sending happy mail to recipients: {}", mail.getMailList());
        // Here would be the real mail sending logic using JavaMailSender or equivalent
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending gloomy mail to recipients: {}", mail.getMailList());
        // Here would be the real mail sending logic using JavaMailSender or equivalent
    }
}