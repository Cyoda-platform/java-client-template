package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
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
}