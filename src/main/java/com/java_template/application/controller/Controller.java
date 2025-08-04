package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.application.entity.HappyMailJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final AtomicLong happyMailJobIdCounter = new AtomicLong(1); // used locally for job technicalId generation suffix

    // POST /controller/mails - Create Mail entity
    @PostMapping("/mails")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || mail.getMailList() == null || mail.getMailList().isEmpty()) {
                logger.error("Mail creation failed: mailList is null or empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalIdUuid = idFuture.get();
            String technicalId = "mail-" + technicalIdUuid.toString(); // keep original format for reference

            logger.info("Mail created with technicalId: {}", technicalId);

            // Removed processMail call

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error creating Mail entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createMail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/mails/{technicalId} - Retrieve Mail entity
    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<Mail> getMail(@PathVariable String technicalId) {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(stripPrefix(technicalId, "mail-"));
            } catch (IllegalArgumentException ex) {
                logger.error("Invalid technicalId format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Mail not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Extract Mail entity from ObjectNode
            Mail mail = node.traverse().readValueAs(Mail.class);
            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving Mail entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getMail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // POST /controller/happyMailJobs - Create HappyMailJob entity (optional, usually internal)
    @PostMapping("/happyMailJobs")
    public ResponseEntity<Map<String, String>> createHappyMailJob(@RequestBody HappyMailJob job) {
        try {
            if (job == null || job.getMailTechnicalId() == null || job.getMailTechnicalId().isBlank()) {
                logger.error("HappyMailJob creation failed: mailTechnicalId is null or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            job.setStatus("PENDING");
            job.setCreatedAt(LocalDateTime.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    HappyMailJob.ENTITY_NAME,
                    ENTITY_VERSION,
                    job
            );
            UUID technicalIdUuid = idFuture.get();
            String technicalId = "job-" + technicalIdUuid.toString();

            logger.info("HappyMailJob created with technicalId: {}", technicalId);

            // Removed processHappyMailJob call

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createHappyMailJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error creating HappyMailJob entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createHappyMailJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/happyMailJobs/{technicalId} - Retrieve HappyMailJob entity
    @GetMapping("/happyMailJobs/{technicalId}")
    public ResponseEntity<HappyMailJob> getHappyMailJob(@PathVariable String technicalId) {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(stripPrefix(technicalId, "job-"));
            } catch (IllegalArgumentException ex) {
                logger.error("Invalid technicalId format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HappyMailJob.ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("HappyMailJob not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            HappyMailJob job = node.traverse().readValueAs(HappyMailJob.class);
            return ResponseEntity.ok(job);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getHappyMailJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving HappyMailJob entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getHappyMailJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Removed processMail and processHappyMailJob methods and their direct helpers

    private String stripPrefix(String original, String prefix) {
        if (original != null && original.startsWith(prefix)) {
            return original.substring(prefix.length());
        }
        return original;
    }
}
