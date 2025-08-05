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
        log.info("Mail entity created with technicalId {}", technicalId);
        processMail(technicalId, mail);
        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found for technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    @GetMapping(value = "/mail", params = "isHappy")
    public ResponseEntity<List<Mail>> getMailsByHappiness(@RequestParam Boolean isHappy) {
        List<Mail> filteredMails = new ArrayList<>();
        for (Mail mail : mailCache.values()) {
            if (mail.getIsHappy() != null && mail.getIsHappy().equals(isHappy)) {
                filteredMails.add(mail);
            }
        }
        return ResponseEntity.ok(filteredMails);
    }

    private void processMail(String technicalId, Mail mail) {
        // Validate mailList and content again if needed
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail with technicalId {} has empty mailList", technicalId);
            return;
        }
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Mail with technicalId {} has empty content", technicalId);
            return;
        }

        // Criteria checking could be added here if explicit checks are needed
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(technicalId, mail);
        } else if (Boolean.FALSE.equals(mail.getIsHappy())) {
            sendGloomyMail(technicalId, mail);
        } else {
            log.info("Mail with technicalId {} has undefined isHappy status", technicalId);
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        // Simulate sending happy mail
        for (String recipient : mail.getMailList()) {
            log.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
            // Real mail sending logic with JavaMail or external API would go here
        }
        log.info("Completed sending happy mail for technicalId {}", technicalId);
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        // Simulate sending gloomy mail
        for (String recipient : mail.getMailList()) {
            log.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
            // Real mail sending logic with JavaMail or external API would go here
        }
        log.info("Completed sending gloomy mail for technicalId {}", technicalId);
    }
}