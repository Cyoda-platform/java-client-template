package com.java_template.application.controller;

import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/application") // Assigning a unique request mapping path
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper; // For converting ObjectNode to Mail

    // Inject EntityService via constructor
    public Controller(EntityService entityService) {
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/mails")
    public ResponseEntity<String> createMail(@RequestBody Mail newMail) {
        try {
            if (!newMail.isValid()) {
                log.error("Invalid Mail entity provided: mailList is null or empty. Mail: {}", newMail);
                return new ResponseEntity<>("Invalid Mail entity (mailList cannot be empty)", HttpStatus.BAD_REQUEST);
            }

            // Add the mail entity to the external service
            // The isHappy field is expected to be determined by the system, so it might be null initially
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    newMail
            );
            UUID technicalId = idFuture.get(); // Blocking call for prototype simplicity

            log.info("Mail entity created with technicalId: {}", technicalId);

            // Simulate Cyoda automatically calling processMail() after saving the entity
            // Note: In a full EDA, this process would typically be triggered by an event
            // and might involve creating new entity versions for status updates.
            // For this prototype, we're modifying the in-memory object passed to processMail
            // but not persisting this specific 'isHappy' update back to EntityService for this technicalId
            // as per "no update" instruction unless explicitly requested.
            processMail(technicalId.toString(), newMail);

            return new ResponseEntity<>(technicalId.toString(), HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            log.error("Bad request for mail creation: {}", e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error creating mail entity due to service call: {}", e.getMessage(), e);
            Thread.currentThread().interrupt(); // Restore the interrupted status
            return new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Unexpected error creating mail entity: {}", e.getMessage(), e);
            return new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/mails/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id); // Validate UUID format
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode mailNode = itemFuture.get(); // Blocking call for prototype simplicity

            if (mailNode == null) {
                log.warn("Mail entity with technicalId {} not found.", id);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            // Convert ObjectNode retrieved from EntityService to Mail entity
            Mail mail = objectMapper.treeToValue(mailNode, Mail.class);
            // Note: The 'isHappy' field in the retrieved Mail object will reflect its state
            // when it was initially added via entityService.addItem(), as no explicit update
            // mechanism to persist 'isHappy' was requested for this prototype.

            log.info("Retrieved Mail entity with technicalId: {}", id);
            return new ResponseEntity<>(mail, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for mail ID: {}. Error: {}", id, e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving mail entity {} due to service call: {}", id, e.getMessage(), e);
            Thread.currentThread().interrupt();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Unexpected error retrieving mail entity {}: {}", id, e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Criteria methods
    private boolean checkMailIsHappy(Mail mail) {
        // Placeholder logic based on functional requirements: mail is happy if mailList has an even number of recipients
        boolean isHappy = mail.getMailList() != null && mail.getMailList().size() % 2 == 0;
        log.info("CheckMailIsHappy for mail with {} recipients: {}", mail.getMailList() != null ? mail.getMailList().size() : 0, isHappy);
        return isHappy;
    }

    private boolean checkMailIsGloomy(Mail mail) {
        // Placeholder logic based on functional requirements: mail is gloomy if mailList has an odd number of recipients
        boolean isGloomy = mail.getMailList() != null && mail.getMailList().size() % 2 != 0;
        log.info("CheckMailIsGloomy for mail with {} recipients: {}", mail.getMailList() != null ? mail.getMailList().size() : 0, isGloomy);
        return isGloomy;
    }

    // Processor methods
    private void sendHappyMail(Mail mail) {
        log.info("Sending happy mail to: {}", mail.getMailList());
        // Simulate external API call or actual mail sending
        // In a real application, this would involve a mail client or service
    }

    private void sendGloomyMail(Mail mail) {
        log.info("Sending gloomy mail to: {}", mail.getMailList());
        // Simulate external API call or actual mail sending
        // In a real application, this would involve a mail client or service
    }

    // Main process method triggered by entity creation
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail entity with technicalId: {}", technicalId);
        try {
            // Criteria Evaluation
            boolean isHappy = checkMailIsHappy(mail);
            boolean isGloomy = checkMailIsGloomy(mail);

            if (isHappy) {
                mail.setIsHappy(true); // Set isHappy on the in-memory 'mail' object
                sendHappyMail(mail);
                log.info("Mail {} classified as HAPPY. Status: HAPPY_MAIL_SENT", technicalId);
            } else if (isGloomy) {
                mail.setIsHappy(false); // Set isHappy on the in-memory 'mail' object
                sendGloomyMail(mail);
                log.info("Mail {} classified as GLOOMY. Status: GLOOMY_MAIL_SENT", technicalId);
            } else {
                log.warn("Mail {} could not be classified as happy or gloomy. Status: UNCLASSIFIED", technicalId);
            }
            // IMPORTANT: As per instructions, direct updates to existing entities are avoided.
            // If the 'isHappy' status or a 'status' field needed to be persisted for the Mail entity
            // after processing, a new entity version or a separate status-tracking entity/event
            // would typically be created via entityService.addItem() in a true event-driven architecture.
            // For this prototype, the 'isHappy' change here is on the in-memory 'mail' object only
            // and is not automatically persisted back to EntityService under the same technicalId.

        } catch (Exception e) {
            log.error("Error processing mail entity {}: {}", technicalId, e.getMessage(), e);
            // In a real system, you might trigger a compensation event or log more details
        }
    }
}