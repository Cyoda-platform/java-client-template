package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.DigestJob;
import com.java_template.application.entity.PetDataRecord;
import com.java_template.application.entity.EmailDispatch;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // DigestJob cache and ID counter
    private final ConcurrentHashMap<String, DigestJob> digestJobCache = new ConcurrentHashMap<>();
    private final AtomicLong digestJobIdCounter = new AtomicLong(1);

    // PetDataRecord cache and ID counter
    private final ConcurrentHashMap<String, PetDataRecord> petDataRecordCache = new ConcurrentHashMap<>();
    private final AtomicLong petDataRecordIdCounter = new AtomicLong(1);

    // EmailDispatch cache and ID counter
    private final ConcurrentHashMap<String, EmailDispatch> emailDispatchCache = new ConcurrentHashMap<>();
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    // ----------------- DigestJob Endpoints -----------------

    @PostMapping("/digestJob")
    public ResponseEntity<?> createDigestJob(@RequestBody DigestJob digestJob) {
        if (digestJob == null) {
            log.error("Received null DigestJob");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("DigestJob payload cannot be null");
        }
        // Generate IDs
        String id = String.valueOf(digestJobIdCounter.getAndIncrement());
        digestJob.setId(id);
        digestJob.setTechnicalId(UUID.randomUUID());
        digestJob.setStatus("PENDING");
        digestJob.setRequestTimestamp(java.time.LocalDateTime.now());

        if (!digestJob.isValid()) {
            log.error("Invalid DigestJob data: {}", digestJob);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid DigestJob data");
        }

        digestJobCache.put(id, digestJob);
        log.info("Created DigestJob with ID: {}", id);

        processDigestJob(digestJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(digestJob);
    }

    @GetMapping("/digestJob/{id}")
    public ResponseEntity<?> getDigestJob(@PathVariable String id) {
        DigestJob job = digestJobCache.get(id);
        if (job == null) {
            log.error("DigestJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // ----------------- PetDataRecord Endpoints -----------------

    @PostMapping("/petDataRecord")
    public ResponseEntity<?> createPetDataRecord(@RequestBody PetDataRecord petDataRecord) {
        if (petDataRecord == null) {
            log.error("Received null PetDataRecord");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("PetDataRecord payload cannot be null");
        }
        String id = String.valueOf(petDataRecordIdCounter.getAndIncrement());
        petDataRecord.setId(id);
        petDataRecord.setTechnicalId(UUID.randomUUID());
        petDataRecord.setStatus("NEW");

        if (!petDataRecord.isValid()) {
            log.error("Invalid PetDataRecord data: {}", petDataRecord);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PetDataRecord data");
        }

        petDataRecordCache.put(id, petDataRecord);
        log.info("Created PetDataRecord with ID: {}", id);

        processPetDataRecord(petDataRecord);

        return ResponseEntity.status(HttpStatus.CREATED).body(petDataRecord);
    }

    @GetMapping("/petDataRecord/{id}")
    public ResponseEntity<?> getPetDataRecord(@PathVariable String id) {
        PetDataRecord record = petDataRecordCache.get(id);
        if (record == null) {
            log.error("PetDataRecord not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetDataRecord not found");
        }
        return ResponseEntity.ok(record);
    }

    // ----------------- EmailDispatch Endpoints -----------------

    @PostMapping("/emailDispatch")
    public ResponseEntity<?> createEmailDispatch(@RequestBody EmailDispatch emailDispatch) {
        if (emailDispatch == null) {
            log.error("Received null EmailDispatch");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("EmailDispatch payload cannot be null");
        }
        String id = String.valueOf(emailDispatchIdCounter.getAndIncrement());
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

    private void processDigestJob(DigestJob digestJob) {
        log.info("Processing DigestJob with ID: {}", digestJob.getId());

        // Validation of petDataQuery and emailRecipients
        if (digestJob.getPetDataQuery().isBlank()) {
            log.error("DigestJob petDataQuery is blank");
            digestJob.setStatus("FAILED");
            digestJobCache.put(digestJob.getId(), digestJob);
            return;
        }
        if (digestJob.getEmailRecipients() == null || digestJob.getEmailRecipients().isEmpty()) {
            log.error("DigestJob emailRecipients list is empty");
            digestJob.setStatus("FAILED");
            digestJobCache.put(digestJob.getId(), digestJob);
            return;
        }

        digestJob.setStatus("PROCESSING");
        digestJobCache.put(digestJob.getId(), digestJob);

        // Simulate fetching pet data from external API based on petDataQuery
        // For prototype, we simulate with dummy data
        List<PetDataRecord> fetchedPetData = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            PetDataRecord petDataRecord = new PetDataRecord();
            petDataRecord.setId(String.valueOf(petDataRecordIdCounter.getAndIncrement()));
            petDataRecord.setTechnicalId(UUID.randomUUID());
            petDataRecord.setJobId(digestJob.getId());
            petDataRecord.setPetId(100 + i);
            petDataRecord.setName("PetName" + i);
            petDataRecord.setCategory("Category" + i);
            petDataRecord.setStatus("NEW");
            petDataRecordCache.put(petDataRecord.getId(), petDataRecord);
            fetchedPetData.add(petDataRecord);

            processPetDataRecord(petDataRecord);
        }

        // After processing all pet data records, create EmailDispatch entries to send emails
        for (String recipient : digestJob.getEmailRecipients()) {
            EmailDispatch emailDispatch = new EmailDispatch();
            emailDispatch.setId(String.valueOf(emailDispatchIdCounter.getAndIncrement()));
            emailDispatch.setTechnicalId(UUID.randomUUID());
            emailDispatch.setJobId(digestJob.getId());
            emailDispatch.setRecipient(recipient);
            emailDispatch.setSubject("Pet Data Digest");
            emailDispatch.setBody("Digest of pet data for your query: " + digestJob.getPetDataQuery());
            emailDispatch.setStatus("QUEUED");
            emailDispatchCache.put(emailDispatch.getId(), emailDispatch);

            processEmailDispatch(emailDispatch);
        }

        digestJob.setStatus("COMPLETED");
        digestJobCache.put(digestJob.getId(), digestJob);

        log.info("Completed processing DigestJob with ID: {}", digestJob.getId());
    }

    private void processPetDataRecord(PetDataRecord petDataRecord) {
        log.info("Processing PetDataRecord with ID: {}", petDataRecord.getId());

        // Transform or enrich data as needed (prototype only sets status)
        petDataRecord.setStatus("PROCESSED");
        petDataRecordCache.put(petDataRecord.getId(), petDataRecord);

        log.info("PetDataRecord processed with ID: {}", petDataRecord.getId());
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