package com.java_template.application.controller;

import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition; // Keep if other methods might use it in future, or remove if strictly not used by remaining code
import com.java_template.common.util.SearchConditionRequest; // Keep if other methods might use it in future, or remove if strictly not used by remaining code
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

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

            // In a full EDA, processing would be triggered by an event after entity persistence.
            // The call to processMail has been removed from here as per the task to extract workflow prototypes.

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

            log.info("Retrieved Mail entity with technicalId: {}", id);
            return new ResponseEntity<>(mail, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for mail ID: {}. Error: {}", id, e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON for mail entity {}: {}", id, e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving mail entity {} due to service call: {}", id, e.getMessage(), e);
            Thread.currentThread().interrupt();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("Unexpected error retrieving mail entity {}: {}", id, e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}