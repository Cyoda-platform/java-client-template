package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || !mail.isValid()) {
                logger.error("Invalid mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.join();
            logger.info("Mail entity created with technicalId: {}", technicalId);

            processMail(technicalId.toString(), mail);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument exception on createMail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error creating mail entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<MailResponse> getMailById(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    id
            );
            ObjectNode node = itemFuture.join();
            if (node == null) {
                logger.error("Mail entity not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Mail mail = node.traverse().readValueAs(Mail.class);
            MailResponse response = new MailResponse(technicalId, mail, "COMPLETED");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument exception on getMailById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error retrieving mail entity with technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(String technicalId, Mail mail) {
        // Business logic for processing mail entity
        try {
            logger.info("Processing mail with technicalId: {}", technicalId);

            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(mail);
                logger.info("Happy mail sent for technicalId: {}", technicalId);
            } else {
                sendGloomyMail(mail);
                logger.info("Gloomy mail sent for technicalId: {}", technicalId);
            }

            // todo: update or notify system if needed - currently immutable data principle

        } catch (Exception e) {
            logger.error("Error processing mail with technicalId: {}", technicalId, e);
        }
    }

    private void sendHappyMail(Mail mail) {
        logger.info("Sending HAPPY mail to recipients: {}", mail.getMailList());
        logger.info("Subject: {}", mail.getSubject());
        logger.info("Content: {}", mail.getContent());
    }

    private void sendGloomyMail(Mail mail) {
        logger.info("Sending GLOOMY mail to recipients: {}", mail.getMailList());
        logger.info("Subject: {}", mail.getSubject());
        logger.info("Content: {}", mail.getContent());
    }

    @Data
    @AllArgsConstructor
    private static class MailResponse {
        private String technicalId;
        private Boolean isHappy;
        private List<String> mailList;
        private String subject;
        private String content;
        private String status;

        public MailResponse(String technicalId, Mail mail, String status) {
            this.technicalId = technicalId;
            this.isHappy = mail.getIsHappy();
            this.mailList = mail.getMailList();
            this.subject = mail.getSubject();
            this.content = mail.getContent();
            this.status = status;
        }
    }
}