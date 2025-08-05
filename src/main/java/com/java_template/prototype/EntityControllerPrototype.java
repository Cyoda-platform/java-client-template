package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.MailJob;
import com.java_template.application.entity.Mail;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, MailJob> mailJobCache = new ConcurrentHashMap<>();
    private final AtomicLong mailJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    // POST /prototype/mailJobs - create MailJob, trigger processing
    @PostMapping("/mailJobs")
    public ResponseEntity<Map<String, String>> createMailJob(@RequestBody MailJob mailJob) {
        if (mailJob == null || !mailJob.isValid()) {
            log.error("Invalid MailJob entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailJobIdCounter.getAndIncrement());
        mailJob.setStatus("PENDING");
        mailJobCache.put(technicalId, mailJob);
        log.info("MailJob created with ID: {}", technicalId);
        try {
            processMailJob(technicalId, mailJob);
        } catch (Exception e) {
            log.error("Error during processing MailJob ID {}: {}", technicalId, e.getMessage());
            mailJob.setStatus("FAILED");
            mailJobCache.put(technicalId, mailJob);
        }
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/mailJobs/{id} - retrieve MailJob by technicalId
    @GetMapping("/mailJobs/{id}")
    public ResponseEntity<MailJob> getMailJobById(@PathVariable String id) {
        MailJob mailJob = mailJobCache.get(id);
        if (mailJob == null) {
            log.error("MailJob with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mailJob);
    }

    // POST /prototype/mails - create Mail entity, trigger processing
    @PostMapping("/mails")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        if (mail == null || !mail.isValid()) {
            log.error("Invalid Mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail created with ID: {}", technicalId);
        try {
            processMail(technicalId, mail);
        } catch (Exception e) {
            log.error("Error during processing Mail ID {}: {}", technicalId, e.getMessage());
        }
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/mails/{id} - retrieve Mail by technicalId
    @GetMapping("/mails/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    // Business logic: processMailJob
    private void processMailJob(String technicalId, MailJob mailJob) {
        log.info("Processing MailJob ID: {}", technicalId);

        // Validate mailList and isHappy already done in isValid()

        // Process sending mails based on isHappy flag
        boolean allSentSuccessfully = true;
        for (String recipient : mailJob.getMailList()) {
            try {
                if (mailJob.getIsHappy()) {
                    sendHappyMail(recipient);
                } else {
                    sendGloomyMail(recipient);
                }
                log.info("Mail sent to {}", recipient);
            } catch (Exception e) {
                log.error("Failed to send mail to {}: {}", recipient, e.getMessage());
                allSentSuccessfully = false;
            }
        }

        // Update status based on sending results
        if (allSentSuccessfully) {
            mailJob.setStatus("COMPLETED");
        } else {
            mailJob.setStatus("FAILED");
        }
        mailJobCache.put(technicalId, mailJob);

        // Optional: Log notification or event for audit
        log.info("MailJob ID {} processing completed with status {}", technicalId, mailJob.getStatus());
    }

    // Business logic: processMail
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail ID: {}", technicalId);

        // Validate mailList and isHappy already done in isValid()

        // Send mail based on isHappy flag
        for (String recipient : mail.getMailList()) {
            try {
                if (mail.getIsHappy()) {
                    sendHappyMail(recipient);
                } else {
                    sendGloomyMail(recipient);
                }
                log.info("Mail sent to {}", recipient);
            } catch (Exception e) {
                log.error("Failed to send mail to {}: {}", recipient, e.getMessage());
            }
        }

        // Optional: Persist mail send status or create related events
        log.info("Mail ID {} processing completed", technicalId);
    }

    // Simulate sending happy mail
    private void sendHappyMail(String recipient) {
        // Simulated logic for sending happy mail
        log.info("Sending happy mail to {}", recipient);
    }

    // Simulate sending gloomy mail
    private void sendGloomyMail(String recipient) {
        // Simulated logic for sending gloomy mail
        log.info("Sending gloomy mail to {}", recipient);
    }
}