```java
package com.java_template.application.controller.mail.version_1;

import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/mail/v1")
@RequiredArgsConstructor
public class MailController {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(MailController.class);

    @Operation(summary = "Add a new mail", description = "Add a new mail entity.")
    @PostMapping
    public ResponseEntity<UUID> addMail(@RequestBody AddMailRequest request) {
        try {
            Mail mail = new Mail();
            mail.setIsHappy(request.getIsHappy());
            mail.setMailList(request.getMailList());

            UUID entityId = entityService.addItem(Mail.ENTITY_NAME, Mail.ENTITY_VERSION, mail).get();
            return ResponseEntity.status(HttpStatus.CREATED).body(entityId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get mail by ID", description = "Get a mail entity by its ID.")
    @GetMapping("/{technicalId}")
    public ResponseEntity<MailResponse> getMailById(@Parameter(description = "Technical ID of the mail entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            DataPayload dataPayload = entityService.getItem(id).get();

            if (dataPayload != null) {
                MailResponse response = objectMapper.treeToValue(dataPayload.getData(), MailResponse.class);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get all mails", description = "Retrieve all mail entities.")
    @GetMapping
    public ResponseEntity<List<MailResponse>> getAllMails() {
        try {
            List<DataPayload> dataPayloads = entityService.getItems(Mail.ENTITY_NAME, Mail.ENTITY_VERSION, null, null, null).get();
            List<MailResponse> responses = dataPayloads.stream()
                .map(dataPayload -> {
                    try {
                        return objectMapper.treeToValue(dataPayload.getData(), MailResponse.class);
                    } catch (Exception e) {
                        logger.error("Error processing mail data", e);
                        return null;
                    }
                })
                .toList();
            return ResponseEntity.ok(responses);
        } catch (ExecutionException e) {
            logger.error("Execution error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    public static class AddMailRequest {
        private Boolean isHappy;
        private List<String> mailList;
    }

    @Data
    public static class MailResponse {
        private Boolean isHappy;
        private List<String> mailList;
    }
}
```