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
        if (!mail.isValid()) {
            log.error("Invalid Mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String technicalId = Long.toString(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);

        log.info("Mail entity created with technicalId: {}", technicalId);

        processMail(technicalId, mail);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mail/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail entity not found for technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    @GetMapping("/mail")
    public ResponseEntity<List<Mail>> getMailByIsHappy(@RequestParam(name = "isHappy", required = false) Boolean isHappy) {
        if (isHappy == null) {
            log.error("Missing required query parameter: isHappy");
            return ResponseEntity.badRequest().build();
        }
        List<Mail> filteredMails = new ArrayList<>();
        for (Mail mail : mailCache.values()) {
            if (isHappy.equals(mail.getIsHappy())) {
                filteredMails.add(mail);
            }
        }
        return ResponseEntity.ok(filteredMails);
    }

    private void processMail(String technicalId, Mail mail) {
        if (mail.getIsHappy()) {
            if (checkMailHappyCriteria(mail)) {
                sendHappyMail(mail);
                log.info("Processed happy mail with technicalId: {}", technicalId);
            } else {
                log.error("Mail with technicalId {} failed happy criteria check", technicalId);
            }
        } else {
            if (checkMailGloomyCriteria(mail)) {
                sendGloomyMail(mail);
                log.info("Processed gloomy mail with technicalId: {}", technicalId);
            } else {
                log.error("Mail with technicalId {} failed gloomy criteria check", technicalId);
            }
        }
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        // Example criteria: content contains positive keywords (simple example)
        String contentLower = mail.getContent().toLowerCase();
        return contentLower.contains("happy") || contentLower.contains("joy") || contentLower.contains("good");
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // Example criteria: content contains negative keywords (simple example)
        String contentLower = mail.getContent().toLowerCase();
        return contentLower.contains("sad") || contentLower.contains("bad") || contentLower.contains("gloom");
    }

    private void sendHappyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            log.info("Sending happy mail to {}", recipient);
            // Here would be the real email send logic using JavaMailSender or similar
        }
    }

    private void sendGloomyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            log.info("Sending gloomy mail to {}", recipient);
            // Here would be the real email send logic using JavaMailSender or similar
        }
    }
}