package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                log.error("Mail list is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList must not be empty"));
            }
            if (mail.getIsHappy() == null) {
                log.error("isHappy flag is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "isHappy must be set"));
            }

            mail.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "Mail",
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.join();

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Object> getMailById(@PathVariable String technicalId) {
        try {
            ObjectNode item = entityService.getItem(
                    "Mail",
                    ENTITY_VERSION,
                    UUID.fromString(technicalId)
            ).join();

            if (item == null || item.isEmpty()) {
                log.error("Mail with technicalId {} not found", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }

            return ResponseEntity.ok(item);

        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
} 