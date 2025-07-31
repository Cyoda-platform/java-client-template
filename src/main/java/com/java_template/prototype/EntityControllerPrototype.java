package com.java_template.prototype;

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
    public ResponseEntity<String> createMail(@RequestBody Mail mail) {
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        processMail(technicalId, mail);
        return ResponseEntity.status(HttpStatus.CREATED).body(technicalId);
    }

    @GetMapping("/mail/{id}")
    public ResponseEntity<Mail> getMail(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing mail with technicalId: {}", technicalId);
        if (mail.isHappy()) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        log.info("Sending Happy Mail: {}", technicalId);
        // Implement Happy Mail sending logic
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        log.info("Sending Gloomy Mail: {}", technicalId);
        // Implement Gloomy Mail sending logic
    }
}