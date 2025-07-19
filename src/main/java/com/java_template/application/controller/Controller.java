package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;

    private static final String DIGEST_REQUEST_JOB_MODEL = "DigestRequestJob";
    private static final String DIGEST_DATA_MODEL = "DigestData";
    private static final String EMAIL_DISPATCH_MODEL = "EmailDispatch";

    // --- DigestRequestJob endpoints ---

    @PostMapping("/digest-request-job")
    public CompletableFuture<ResponseEntity<?>> createDigestRequestJob(@RequestBody DigestRequestJob requestJob) {
        if (requestJob == null) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body cannot be null"));
        }
        if (requestJob.getEmail() == null || requestJob.getEmail().isBlank()) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is required"));
        }
        requestJob.setStatus(DigestRequestJob.StatusEnum.PENDING);
        return entityService.addItem(DIGEST_REQUEST_JOB_MODEL, ENTITY_VERSION, requestJob)
                .thenApply(id -> {
                    requestJob.setTechnicalId(id);
                    log.info("Created DigestRequestJob with technicalId: {}", id);
                    return ResponseEntity.status(HttpStatus.CREATED).body(requestJob);
                });
    }

    @GetMapping("/digest-request-job/{id}")
    public CompletableFuture<ResponseEntity<?>> getDigestRequestJob(@PathVariable("id") UUID id) {
        return entityService.getItem(DIGEST_REQUEST_JOB_MODEL, ENTITY_VERSION, id)
                .thenApply(itemNode -> {
                    if (itemNode == null || itemNode.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequestJob not found");
                    }
                    return ResponseEntity.ok(itemNode);
                });
    }

    // --- DigestData endpoints ---

    @PostMapping("/digest-data")
    public CompletableFuture<ResponseEntity<?>> createDigestData(@RequestBody DigestData digestData) {
        if (digestData == null) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body cannot be null"));
        }
        if (digestData.getJobId() == null || digestData.getJobId().isBlank()) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("JobId is required"));
        }
        digestData.setStatus(DigestData.StatusEnum.RETRIEVED);
        return entityService.addItem(DIGEST_DATA_MODEL, ENTITY_VERSION, digestData)
                .thenApply(id -> {
                    digestData.setTechnicalId(id);
                    log.info("Created DigestData with technicalId: {}", id);
                    return ResponseEntity.status(HttpStatus.CREATED).body(digestData);
                });
    }

    @GetMapping("/digest-data/{id}")
    public CompletableFuture<ResponseEntity<?>> getDigestData(@PathVariable("id") UUID id) {
        return entityService.getItem(DIGEST_DATA_MODEL, ENTITY_VERSION, id)
                .thenApply(itemNode -> {
                    if (itemNode == null || itemNode.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestData not found");
                    }
                    return ResponseEntity.ok(itemNode);
                });
    }

    // --- EmailDispatch endpoints ---

    @PostMapping("/email-dispatch")
    public CompletableFuture<ResponseEntity<?>> createEmailDispatch(@RequestBody EmailDispatch emailDispatch) {
        if (emailDispatch == null) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body cannot be null"));
        }
        if (emailDispatch.getJobId() == null || emailDispatch.getJobId().isBlank()) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("JobId is required"));
        }
        if (emailDispatch.getEmailFormat() == null) {
            return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("EmailFormat is required"));
        }
        emailDispatch.setStatus(EmailDispatch.StatusEnum.QUEUED);
        return entityService.addItem(EMAIL_DISPATCH_MODEL, ENTITY_VERSION, emailDispatch)
                .thenApply(id -> {
                    emailDispatch.setTechnicalId(id);
                    log.info("Created EmailDispatch with technicalId: {}", id);
                    return ResponseEntity.status(HttpStatus.CREATED).body(emailDispatch);
                });
    }

    @GetMapping("/email-dispatch/{id}")
    public CompletableFuture<ResponseEntity<?>> getEmailDispatch(@PathVariable("id") UUID id) {
        return entityService.getItem(EMAIL_DISPATCH_MODEL, ENTITY_VERSION, id)
                .thenApply(itemNode -> {
                    if (itemNode == null || itemNode.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EmailDispatch not found");
                    }
                    return ResponseEntity.ok(itemNode);
                });
    }
}