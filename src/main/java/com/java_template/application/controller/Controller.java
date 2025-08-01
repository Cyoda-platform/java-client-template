```java
package com.java_template.application.controller;

import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
public class Controller {

    private final EntityService entityService;

    @Autowired
    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<String> createMail(@RequestBody Mail mail) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get();
            processMail(technicalId, mail);
            log.info("Mail created with technicalId: {}", technicalId);
            return new ResponseEntity<>(technicalId.toString(), HttpStatus.CREATED);
        } catch (InterruptedException | ExecutionException | IllegalArgumentException e) {
            log.error("Error creating mail: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error creating mail: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Mail> getMail(@PathVariable String id) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, UUID.fromString(id));
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("Mail not found with technicalId: {}", id);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            Mail mail = convertObjectNodeToMail(node);
            log.info("Mail retrieved with technicalId: {}", id);
            return new ResponseEntity<>(mail, HttpStatus.OK);
        } catch (InterruptedException | ExecutionException | IllegalArgumentException e) {
            log.error("Error getting mail: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error getting mail: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void processMail(UUID technicalId, Mail mail) {
        log.info("Processing mail with technicalId: {}", technicalId);

        if (mail.getIsHappy()) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }
    }

    private void sendHappyMail(UUID technicalId, Mail mail) {
        log.info("Sending happy mail with technicalId: {} to: {} with content: {}", technicalId, mail.getMailList(), mail.getContentHappy());
        // Add actual email sending logic here using JavaMail or similar
    }

    private void sendGloomyMail(UUID technicalId, Mail mail) {
        log.info("Sending gloomy mail with technicalId: {} to: {} with content: {}", technicalId, mail.getMailList(), mail.getContentGloomy());
        // Add actual email sending logic here using JavaMail or similar
    }

    private Mail convertObjectNodeToMail(ObjectNode node) {
        Mail mail = new Mail();
        if (node.get("isHappy") != null) {
            mail.setIsHappy(node.get("isHappy").asBoolean());
        }
        if (node.get("mailList") != null) {
            ArrayNode mailListArray = (ArrayNode) node.get("mailList");
            List<String> mailList = new ArrayList<>();
            for (int i = 0; i < mailListArray.size(); i++) {
                mailList.add(mailListArray.get(i).asText());
            }
            mail.setMailList(mailList);
        }
        if (node.get("contentHappy") != null) {
            mail.setContentHappy(node.get("contentHappy").asText());
        }
        if (node.get("contentGloomy") != null) {
            mail.setContentGloomy(node.get("contentGloomy").asText());
        }
        return mail;
    }

    @GetMapping("/all")
    public ResponseEntity<List<Mail>> getAllMails() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Mail.ENTITY_NAME, ENTITY_VERSION);
            ArrayNode nodes = itemsFuture.get();
            List<Mail> mails = new ArrayList<>();
            if (nodes != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    mails.add(convertObjectNodeToMail((ObjectNode) nodes.get(i)));
                }
            }
            log.info("All Mails retrieved");
            return new ResponseEntity<>(mails, HttpStatus.OK);
        } catch (InterruptedException | ExecutionException | IllegalArgumentException e) {
            log.error("Error getting all mails: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error getting all mails: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @GetMapping("/byCondition")
    public ResponseEntity<List<Mail>> getMailsByCondition(@RequestParam String field, @RequestParam String value) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.".concat(field), "EQUALS", value)
            );

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    condition,
                    true
            );

            ArrayNode nodes = filteredItemsFuture.get();
            List<Mail> mails = new ArrayList<>();
            if (nodes != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    mails.add(convertObjectNodeToMail((ObjectNode) nodes.get(i)));
                }
            }
            log.info("Mails retrieved by condition");
            return new ResponseEntity<>(mails, HttpStatus.OK);
        } catch (InterruptedException | ExecutionException | IllegalArgumentException e) {
            log.error("Error getting mails by condition: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error getting mails by condition: {}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
```