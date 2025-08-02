package com.java_template.application.controller;

import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/mail")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (!mail.isValid()) {
                log.error("Invalid Mail entity received: {}", mail);
                return new ResponseEntity<>(Map.of("error", "Invalid mail data"), HttpStatus.BAD_REQUEST);
            }

            // Set initial status before saving to EntityService
            mail.setStatus("PENDING");

            // Save the mail entity using EntityService.addItem
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.get(); // Block to get the UUID. In a real async flow, this would be non-blocking.
            log.info("Mail entity created with technicalId: {}", technicalId);

            // Trigger the appropriate process based on isHappy criteria.
            // In a real Cyoda system, these processors would be triggered by internal events
            // following entity persistence, not directly from the controller.
            if (checkMailHappy(mail)) {
                processMailSendHappyMail(technicalId, mail);
            } else if (checkMailGloomy(mail)) {
                processMailSendGloomyMail(technicalId, mail);
            } else {
                log.error("No matching criteria for mail with technicalId: {}", technicalId);
                // The status will remain "PENDING" as set initially, since no criteria matched.
                return new ResponseEntity<>(Map.of("error", "No matching criteria for mail"), HttpStatus.BAD_REQUEST);
            }

            return new ResponseEntity<>(Map.of("technicalId", technicalId.toString()), HttpStatus.CREATED);

        } catch (IllegalArgumentException e) {
            log.error("Bad request for createMail: {}", e.getMessage());
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Internal server error during createMail: {}", e.getMessage(), e);
            return new ResponseEntity<>(Map.of("error", "Internal server error"), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<Mail> getMail(@PathVariable UUID technicalId) {
        try {
            // Retrieve the mail entity using EntityService.getItem
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode mailNode = itemFuture.get(); // Block to get the item.
            if (mailNode == null) {
                log.warn("Mail with technicalId: {} not found", technicalId);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            // Convert ObjectNode (which includes technicalId and other fields) to Mail entity
            Mail mail = objectMapper.treeToValue(mailNode, Mail.class);
            log.info("Retrieved Mail entity with technicalId: {}", technicalId);
            return new ResponseEntity<>(mail, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            log.error("Bad request for getMail: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (ExecutionException | InterruptedException e) {
            log.error("Error retrieving mail for technicalId {}: {}", technicalId, e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Internal server error during getMail: {}", e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Criteria methods to determine if a mail is happy or gloomy
    private boolean checkMailHappy(Mail mail) {
        return mail.getIsHappy() != null && mail.getIsHappy();
    }

    private boolean checkMailGloomy(Mail mail) {
        return mail.getIsHappy() != null && !mail.getIsHappy();
    }

    // Processor methods - these simulate the business logic without persisting status updates
    // back to the EntityService, adhering to the 'no update' rule for this prototype.
    private void processMailSendHappyMail(UUID technicalId, Mail mail) {
        log.info("Processing happy mail for technicalId: {}", technicalId);
        try {
            // Simulate sending happy mail to external service
            log.info("Simulating sending happy mail to: {}", mail.getMailList());
            // In a full implementation, this would involve an actual external API call.
            // For example: externalMailService.sendHappyMail(mail.getMailList(), mail.getContent());

            // Update status internally for logging and immediate reflection, but this change
            // is NOT persisted back to the EntityService in this prototype.
            mail.setStatus("SENT_HAPPY");
            log.info("Simulated happy mail sent successfully for technicalId: {}. Mail status: {}", technicalId, mail.getStatus());
        } catch (Exception e) {
            log.error("Failed to simulate sending happy mail for technicalId: {}. Error: {}", technicalId, e.getMessage());
            mail.setStatus("FAILED");
            log.error("Simulated happy mail failed for technicalId: {}. Mail status: {}", technicalId, mail.getStatus());
        }
    }

    private void processMailSendGloomyMail(UUID technicalId, Mail mail) {
        log.info("Processing gloomy mail for technicalId: {}", technicalId);
        try {
            // Simulate sending gloomy mail to external service
            log.info("Simulating sending gloomy mail to: {}", mail.getMailList());
            // In a full implementation, this would involve an actual external API call.
            // For example: externalMailService.sendGloomyMail(mail.getMailList(), mail.getContent());

            // Update status internally for logging and immediate reflection, but this change
            // is NOT persisted back to the EntityService in this prototype.
            mail.setStatus("SENT_GLOOMY");
            log.info("Simulated gloomy mail sent successfully for technicalId: {}. Mail status: {}", technicalId, mail.getStatus());
        } catch (Exception e) {
            log.error("Failed to simulate sending gloomy mail for technicalId: {}. Error: {}", technicalId, e.getMessage());
            mail.setStatus("FAILED");
            log.error("Simulated gloomy mail failed for technicalId: {}. Mail status: {}", technicalId, mail.getStatus());
        }
    }
}