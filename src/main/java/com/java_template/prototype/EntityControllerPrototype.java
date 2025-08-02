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

    @PostMapping("/mail")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        if (!mail.isValid()) {
            log.error("Invalid Mail entity received: {}", mail);
            return new ResponseEntity<>(Map.of("error", "Invalid mail data"), HttpStatus.BAD_REQUEST);
        }

        String technicalId = "mail-" + mailIdCounter.getAndIncrement();
        mail.setStatus("PENDING");
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);

        // Trigger the appropriate process based on isHappy criteria
        if (checkMailHappy(mail)) {
            processMailSendHappyMail(technicalId, mail);
        } else if (checkMailGloomy(mail)) {
            processMailSendGloomyMail(technicalId, mail);
        } else {
            log.error("No matching criteria for mail with technicalId: {}", technicalId);
            mail.setStatus("FAILED");
            mailCache.put(technicalId, mail); // Update status in cache
            return new ResponseEntity<>(Map.of("error", "No matching criteria for mail"), HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(Map.of("technicalId", technicalId), HttpStatus.CREATED);
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<Mail> getMail(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.warn("Mail with technicalId: {} not found", technicalId);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        log.info("Retrieved Mail entity with technicalId: {}", technicalId);
        return new ResponseEntity<>(mail, HttpStatus.OK);
    }

    private boolean checkMailHappy(Mail mail) {
        return mail.getIsHappy() != null && mail.getIsHappy();
    }

    private boolean checkMailGloomy(Mail mail) {
        return mail.getIsHappy() != null && !mail.getIsHappy();
    }

    private void processMailSendHappyMail(String technicalId, Mail mail) {
        log.info("Processing happy mail for technicalId: {}", technicalId);
        try {
            // Simulate sending happy mail
            log.info("Sending happy mail to: {}", mail.getMailList());
            // In a real application, this would involve an external API call to a mail service
            // For now, we just simulate success.
            mail.setStatus("SENT_HAPPY");
            mailCache.put(technicalId, mail);
            log.info("Happy mail sent successfully for technicalId: {}", technicalId);
        } catch (Exception e) {
            log.error("Failed to send happy mail for technicalId: {}. Error: {}", technicalId, e.getMessage());
            mail.setStatus("FAILED");
            mailCache.put(technicalId, mail);
        }
    }

    private void processMailSendGloomyMail(String technicalId, Mail mail) {
        log.info("Processing gloomy mail for technicalId: {}", technicalId);
        try {
            // Simulate sending gloomy mail
            log.info("Sending gloomy mail to: {}", mail.getMailList());
            // In a real application, this would involve an external API call to a mail service
            // For now, we just simulate success.
            mail.setStatus("SENT_GLOOMY");
            mailCache.put(technicalId, mail);
            log.info("Gloomy mail sent successfully for technicalId: {}", technicalId);
        } catch (Exception e) {
            log.error("Failed to send gloomy mail for technicalId: {}. Error: {}", technicalId, e.getMessage());
            mail.setStatus("FAILED");
            mailCache.put(technicalId, mail);
        }
    }
}