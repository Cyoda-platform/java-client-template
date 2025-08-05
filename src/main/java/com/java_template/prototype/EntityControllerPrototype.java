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
        try {
            if (mail == null || !mail.isValid()) {
                log.error("Invalid Mail entity submitted");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String technicalId = Long.toString(mailIdCounter.getAndIncrement());
            mailCache.put(technicalId, mail);
            log.info("Mail entity created with technicalId: {}", technicalId);
            processMail(technicalId, mail);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating Mail entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Retrieved Mail entity for technicalId: {}", technicalId);
        return ResponseEntity.ok(mail);
    }

    @GetMapping("/mail")
    public ResponseEntity<List<Mail>> getMailsByCriteria(@RequestParam(required = false) Boolean isHappy) {
        List<Mail> result = new ArrayList<>();
        for (Mail mail : mailCache.values()) {
            if (isHappy == null || (mail.getIsHappy() != null && mail.getIsHappy().equals(isHappy))) {
                result.add(mail);
            }
        }
        log.info("Retrieved {} Mail entities by criteria isHappy={}", result.size(), isHappy);
        return ResponseEntity.ok(result);
    }

    private void processMail(String technicalId, Mail mail) {
        // Criteria validation (can be expanded if explicit checks needed)
        boolean happyCriteriaMet = checkMailHappy(mail);
        boolean gloomyCriteriaMet = checkMailGloomy(mail);

        if (happyCriteriaMet) {
            sendHappyMail(technicalId, mail);
        } else if (gloomyCriteriaMet) {
            sendGloomyMail(technicalId, mail);
        } else {
            log.error("Mail entity with technicalId {} failed criteria check", technicalId);
        }
    }

    private boolean checkMailHappy(Mail mail) {
        // Implement criteria for happy mail, here simplified as isHappy == true
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailGloomy(Mail mail) {
        // Implement criteria for gloomy mail, here simplified as isHappy == false
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        // Implement sending happy mail logic
        log.info("Sending happy mail for technicalId: {}, to recipients: {}", technicalId, mail.getMailList());
        // Example: Here you would integrate with JavaMail or external mail service
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        // Implement sending gloomy mail logic
        log.info("Sending gloomy mail for technicalId: {}, to recipients: {}", technicalId, mail.getMailList());
        // Example: Here you would integrate with JavaMail or external mail service
    }
}