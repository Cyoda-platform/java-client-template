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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) throws ExecutionException, InterruptedException {
        try {
            if (mail == null || !mail.isValid()) {
                log.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalId = idFuture.get();

            log.info("Mail entity created with technicalId={}", technicalId);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Exception in createMail: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected exception in createMail: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMail(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("Mail entity not found for technicalId={}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Mail mail = objectMapper.treeToValue(node, Mail.class);
            log.info("Mail entity retrieved for technicalId={}", technicalId);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Exception in getMail: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (JsonProcessingException e) {
            log.error("JsonProcessingException in getMail: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected exception in getMail: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Other CRUD operations and endpoints remain unchanged

}