package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestJob;
import com.java_template.application.entity.PetDataRecord;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;

    private static final String ENTITY_DIGEST_JOB = "DigestJob";
    private static final String ENTITY_PET_DATA_RECORD = "PetDataRecord";

    // EmailDispatch is minor/utility entity, keep local cache for it
    private final Map<String, EmailDispatch> emailDispatchCache = new HashMap<>();
    private long emailDispatchIdCounter = 1L;

    // ----------------- DigestJob Endpoints -----------------

    @PostMapping("/digestJob")
    public ResponseEntity<?> createDigestJob(@RequestBody DigestJob digestJob) throws ExecutionException, InterruptedException {
        if (digestJob == null) {
            log.error("Received null DigestJob");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("DigestJob payload cannot be null");
        }

        digestJob.setStatus("PENDING");
        digestJob.setRequestTimestamp(java.time.LocalDateTime.now());

        if (!digestJob.isValid()) {
            log.error("Invalid DigestJob data: {}", digestJob);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid DigestJob data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_DIGEST_JOB,
                ENTITY_VERSION,
                digestJob
        );
        UUID technicalId = idFuture.get();
        digestJob.setTechnicalId(technicalId);

        log.info("Created DigestJob with technicalId: {}", technicalId);

        processDigestJob(digestJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(digestJob);
    }

    @GetMapping("/digestJob/{id}")
    public ResponseEntity<?> getDigestJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        // id here is technicalId string (UUID string)
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid DigestJob technicalId format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid DigestJob ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_DIGEST_JOB,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            log.error("DigestJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestJob not found");
        }

        DigestJob digestJob = node.traverse().readValueAs(DigestJob.class);
        return ResponseEntity.ok(digestJob);
    }

    // ----------------- PetDataRecord Endpoints -----------------

    @PostMapping("/petDataRecord")
    public ResponseEntity<?> createPetDataRecord(@RequestBody PetDataRecord petDataRecord) throws ExecutionException, InterruptedException {
        if (petDataRecord == null) {
            log.error("Received null PetDataRecord");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetDataRecord payload cannot be null");
        }

        petDataRecord.setStatus("NEW");

        if (!petDataRecord.isValid()) {
            log.error("Invalid PetDataRecord data: {}", petDataRecord);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetDataRecord data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_PET_DATA_RECORD,
                ENTITY_VERSION,
                petDataRecord
        );
        UUID technicalId = idFuture.get();
        petDataRecord.setTechnicalId(technicalId);

        log.info("Created PetDataRecord with technicalId: {}", technicalId);

        processPetDataRecord(petDataRecord);

        return ResponseEntity.status(HttpStatus.CREATED).body(petDataRecord);
    }

    @GetMapping("/petDataRecord/{id}")
    public ResponseEntity<?> getPetDataRecord(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid PetDataRecord technicalId format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetDataRecord ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_PET_DATA_RECORD,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            log.error("PetDataRecord not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetDataRecord not found");
        }

        PetDataRecord petDataRecord = node.traverse().readValueAs(PetDataRecord.class);
        return ResponseEntity.ok(petDataRecord);
    }

    // ----------------- EmailDispatch Endpoints (local cache) -----------------

    @PostMapping("/emailDispatch")
    public ResponseEntity<?> createEmailDispatch(@RequestBody EmailDispatch emailDispatch) {
        if (emailDispatch == null) {
            log.error("Received null EmailDispatch");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("EmailDispatch payload cannot be null");
        }
        String id = String.valueOf(emailDispatchIdCounter++);
        emailDispatch.setId(id);
        emailDispatch.setTechnicalId(UUID.randomUUID());
        emailDispatch.setStatus("QUEUED");

        if (!emailDispatch.isValid()) {
            log.error("Invalid EmailDispatch data: {}", emailDispatch);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid EmailDispatch data");
        }

        emailDispatchCache.put(id, emailDispatch);
        log.info("Created EmailDispatch with ID: {}", id);

        processEmailDispatch(emailDispatch);

        return ResponseEntity.status(HttpStatus.CREATED).body(emailDispatch);
    }

    @GetMapping("/emailDispatch/{id}")
    public ResponseEntity<?> getEmailDispatch(@PathVariable String id) {
        EmailDispatch email = emailDispatchCache.get(id);
        if (email == null) {
            log.error("EmailDispatch not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EmailDispatch not found");
        }
        return ResponseEntity.ok(email);
    }

    // ----------------- Process Methods -----------------

    private void processDigestJob(DigestJob digestJob) throws ExecutionException, InterruptedException {
        log.info("Processing DigestJob with technicalId: {}", digestJob.getTechnicalId());

        if (digestJob.getPetDataQuery() == null || digestJob.getPetDataQuery().isBlank()) {
            log.error("DigestJob petDataQuery is blank");
            digestJob.setStatus("FAILED");
            entityService.updateItem(ENTITY_DIGEST_JOB, ENTITY_VERSION, digestJob.getTechnicalId(), digestJob).get();
            return;
        }
        if (digestJob.getEmailRecipients() == null || digestJob.getEmailRecipients().isEmpty()) {
            log.error("DigestJob emailRecipients list is empty");
            digestJob.setStatus("FAILED");
            entityService.updateItem(ENTITY_DIGEST_JOB, ENTITY_VERSION, digestJob.getTechnicalId(), digestJob).get();
            return;
        }

        digestJob.setStatus("PROCESSING");
        entityService.updateItem(ENTITY_DIGEST_JOB, ENTITY_VERSION, digestJob.getTechnicalId(), digestJob).get();

        // Simulate fetching pet data from external API based on petDataQuery
        // For prototype, we simulate with dummy data

        List<PetDataRecord> fetchedPetData = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            PetDataRecord petDataRecord = new PetDataRecord();
            petDataRecord.setJobId(digestJob.getId());
            petDataRecord.setPetId(100 + i);
            petDataRecord.setName("PetName" + i);
            petDataRecord.setCategory("Category" + i);
            petDataRecord.setStatus("NEW");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_PET_DATA_RECORD,
                    ENTITY_VERSION,
                    petDataRecord
            );
            UUID technicalId = idFuture.get();
            petDataRecord.setTechnicalId(technicalId);
            fetchedPetData.add(petDataRecord);

            processPetDataRecord(petDataRecord);
        }

        // After processing all pet data records, create EmailDispatch entries to send emails
        for (String recipient : digestJob.getEmailRecipients()) {
            EmailDispatch emailDispatch = new EmailDispatch();
            String id = String.valueOf(emailDispatchIdCounter++);
            emailDispatch.setId(id);
            emailDispatch.setTechnicalId(UUID.randomUUID());
            emailDispatch.setJobId(digestJob.getId());
            emailDispatch.setRecipient(recipient);
            emailDispatch.setSubject("Pet Data Digest");
            emailDispatch.setBody("Digest of pet data for your query: " + digestJob.getPetDataQuery());
            emailDispatch.setStatus("QUEUED");
            emailDispatchCache.put(id, emailDispatch);

            processEmailDispatch(emailDispatch);
        }

        digestJob.setStatus("COMPLETED");
        entityService.updateItem(ENTITY_DIGEST_JOB, ENTITY_VERSION, digestJob.getTechnicalId(), digestJob).get();

        log.info("Completed processing DigestJob with technicalId: {}", digestJob.getTechnicalId());
    }

    private void processPetDataRecord(PetDataRecord petDataRecord) throws ExecutionException, InterruptedException {
        log.info("Processing PetDataRecord with technicalId: {}", petDataRecord.getTechnicalId());

        petDataRecord.setStatus("PROCESSED");
        entityService.updateItem(ENTITY_PET_DATA_RECORD, ENTITY_VERSION, petDataRecord.getTechnicalId(), petDataRecord).get();

        log.info("PetDataRecord processed with technicalId: {}", petDataRecord.getTechnicalId());
    }

    private void processEmailDispatch(EmailDispatch emailDispatch) {
        log.info("Processing EmailDispatch with ID: {}", emailDispatch.getId());

        // Simulate sending email (in real implementation, call email service)
        try {
            // Simulated email sending...
            emailDispatch.setStatus("SENT");
            log.info("Email sent to {}", emailDispatch.getRecipient());
        } catch (Exception e) {
            log.error("Failed to send email to {}", emailDispatch.getRecipient(), e);
            emailDispatch.setStatus("FAILED");
        }

        emailDispatchCache.put(emailDispatch.getId(), emailDispatch);
    }
}