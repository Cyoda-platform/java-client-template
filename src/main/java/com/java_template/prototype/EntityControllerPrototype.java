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
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mailRequest) {
        if (mailRequest == null || mailRequest.getMailList() == null || mailRequest.getMailList().isEmpty() || mailRequest.getContent() == null || mailRequest.getContent().isBlank()) {
            log.error("Invalid mail creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String technicalId = Long.toString(mailIdCounter.getAndIncrement());
        Mail newMail = new Mail();
        newMail.setMailList(mailRequest.getMailList());
        newMail.setContent(mailRequest.getContent());
        newMail.setIsHappy(null); // will be set in processing
        mailCache.put(technicalId, newMail);

        log.info("Mail created with technicalId {}", technicalId);

        processMail(technicalId, newMail);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail with technicalId {} not found", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Mail with technicalId {} retrieved", technicalId);
        return ResponseEntity.ok(mail);
    }

    // Process method implementing business logic as per requirements
    private void processMail(String technicalId, Mail mail) {
        // Criteria evaluation: simple happiness criteria example - content contains happy keywords
        String contentLower = mail.getContent().toLowerCase(Locale.ROOT);
        boolean isHappy = contentLower.contains("happy") || contentLower.contains("joy") || contentLower.contains("glad");
        mail.setIsHappy(isHappy);
        log.info("Mail {} happiness evaluated: {}", technicalId, isHappy);

        // Processors
        if (isHappy) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        // Simulate sending happy mail
        log.info("Sending HAPPY mail to {} with content: {}", mail.getMailList(), mail.getContent());
        // Here would be integration with mail sending service
        log.info("Happy mail sent successfully for technicalId {}", technicalId);
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending GLOOMY mail to {} with content: {}", mail.getMailList(), mail.getContent());
        // Here would be integration with mail sending service
        log.info("Gloomy mail sent successfully for technicalId {}", technicalId);
    }
}