package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private static final String ENTITY_NAME = "Mail";

    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Mail mail) throws Exception {
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail creation failed: mailList is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("mailList is required and cannot be empty");
        }

        mail.setStatus("PENDING");

        UUID technicalId = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                mail
        ).get();

        mail.setTechnicalId(technicalId);

        try {
            processMail(mail);
        } catch (Exception e) {
            log.error("Processing mail failed for technicalId {}: {}", technicalId, e.getMessage());
            mail.setStatus("FAILED");
            entityService.updateItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId,
                    mail
            ).get();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing failed");
        }

        entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                technicalId,
                mail
        ).get();

        return ResponseEntity.status(HttpStatus.CREATED).body(mail);
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMail(@PathVariable UUID technicalId) throws Exception {
        ObjectNode mailNode = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                technicalId
        ).get();

        if (mailNode == null || mailNode.isEmpty()) {
            log.error("Mail not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail not found");
        }

        Mail mail = entityService.convertObjectNodeToEntity(mailNode, Mail.class);
        return ResponseEntity.ok(mail);
    }

    private void processMail(Mail mail) {
        log.info("Processing Mail with technicalId: {}", mail.getTechnicalId());

        boolean isHappy = false;
        boolean happyCriteriaMet = checkEntityIsHappy(mail);
        boolean gloomyCriteriaMet = checkEntityIsGloomy(mail);

        if (happyCriteriaMet) {
            isHappy = true;
            mail.setIsHappy(true);
            processMailSendHappyMail(mail);
            mail.setStatus("SENT_HAPPY");
            log.info("Mail technicalId {} sent as Happy", mail.getTechnicalId());
        } else if (gloomyCriteriaMet) {
            isHappy = false;
            mail.setIsHappy(false);
            processMailSendGloomyMail(mail);
            mail.setStatus("SENT_GLOOMY");
            log.info("Mail technicalId {} sent as Gloomy", mail.getTechnicalId());
        } else {
            mail.setStatus("FAILED");
            log.error("Mail technicalId {} does not meet any criteria for sending", mail.getTechnicalId());
            throw new IllegalStateException("Mail does not meet happy or gloomy criteria");
        }
    }

    private boolean checkEntityIsHappy(Mail mail) {
        return mail.getMailList() != null && mail.getMailList().size() > 1;
    }

    private boolean checkEntityIsGloomy(Mail mail) {
        return mail.getMailList() != null && mail.getMailList().size() == 1;
    }

    private void processMailSendHappyMail(Mail mail) {
        log.info("Sending Happy Mail to recipients: {}", mail.getMailList());
        // Real implementation would send emails here
    }

    private void processMailSendGloomyMail(Mail mail) {
        log.info("Sending Gloomy Mail to recipients: {}", mail.getMailList());
        // Real implementation would send emails here
    }
}