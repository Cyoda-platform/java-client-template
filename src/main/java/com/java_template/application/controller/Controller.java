package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import static com.java_template.common.config.Config.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final String ENTITY_NAME = "Mail";

    @PostMapping
    public ResponseEntity<?> createMail(@Valid @RequestBody Mail mail) throws ExecutionException, InterruptedException, JsonProcessingException {
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
    public ResponseEntity<?> getMail(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid = UUID.fromString(technicalId);
        ObjectNode mailNode = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                uuid
        ).get();

        if (mailNode == null || mailNode.isEmpty()) {
            log.error("Mail not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail not found");
        }

        Mail mail = objectMapper.treeToValue(mailNode, Mail.class);
        return ResponseEntity.ok(mail);
    }
}