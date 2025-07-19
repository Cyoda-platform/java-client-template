package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.EmailDispatch;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Caches and ID counters for each entity
    private final ConcurrentHashMap<String, DigestRequestJob> digestRequestJobCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, DigestData> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, EmailDispatch> emailDispatchCache = new ConcurrentHashMap<>();
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    // --- DigestRequestJob endpoints ---

    @PostMapping("/digest-request-job")
    public ResponseEntity<?> createDigestRequestJob(@RequestBody DigestRequestJob requestJob) {
        try {
            if (requestJob == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body cannot be null");
            }
            if (requestJob.getEmail() == null || requestJob.getEmail().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is required");
            }
            String newId = String.valueOf(digestRequestJobIdCounter.getAndIncrement());
            requestJob.setId(newId);
            requestJob.setStatus(DigestRequestJob.StatusEnum.PENDING);
            requestJobCachePut(requestJob);
            log.info("Created DigestRequestJob with ID: {}", newId);
            processDigestRequestJob(requestJob);
            return ResponseEntity.status(HttpStatus.CREATED).body(requestJob);
        } catch (Exception e) {
            log.error("Error creating DigestRequestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @GetMapping("/digest-request-job/{id}")
    public ResponseEntity<?> getDigestRequestJob(@PathVariable("id") String id) {
        DigestRequestJob job = digestRequestJobCache.get(id);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequestJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // --- DigestData endpoints ---

    @PostMapping("/digest-data")
    public ResponseEntity<?> createDigestData(@RequestBody DigestData digestData) {
        try {
            if (digestData == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body cannot be null");
            }
            if (digestData.getJobId() == null || digestData.getJobId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("JobId is required");
            }
            String newId = String.valueOf(digestDataIdCounter.getAndIncrement());
            digestData.setId(newId);
            digestData.setStatus(DigestData.StatusEnum.RETRIEVED);
            digestDataCachePut(digestData);
            log.info("Created DigestData with ID: {}", newId);
            processDigestData(digestData);
            return ResponseEntity.status(HttpStatus.CREATED).body(digestData);
        } catch (Exception e) {
            log.error("Error creating DigestData", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @GetMapping("/digest-data/{id}")
    public ResponseEntity<?> getDigestData(@PathVariable("id") String id) {
        DigestData data = digestDataCache.get(id);
        if (data == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestData not found");
        }
        return ResponseEntity.ok(data);
    }

    // --- EmailDispatch endpoints ---

    @PostMapping("/email-dispatch")
    public ResponseEntity<?> createEmailDispatch(@RequestBody EmailDispatch emailDispatch) {
        try {
            if (emailDispatch == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body cannot be null");
            }
            if (emailDispatch.getJobId() == null || emailDispatch.getJobId().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("JobId is required");
            }
            if (emailDispatch.getEmailFormat() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("EmailFormat is required");
            }
            String newId = String.valueOf(emailDispatchIdCounter.getAndIncrement());
            emailDispatch.setId(newId);
            emailDispatch.setStatus(EmailDispatch.StatusEnum.QUEUED);
            emailDispatchCachePut(emailDispatch);
            log.info("Created EmailDispatch with ID: {}", newId);
            processEmailDispatch(emailDispatch);
            return ResponseEntity.status(HttpStatus.CREATED).body(emailDispatch);
        } catch (Exception e) {
            log.error("Error creating EmailDispatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @GetMapping("/email-dispatch/{id}")
    public ResponseEntity<?> getEmailDispatch(@PathVariable("id") String id) {
        EmailDispatch dispatch = emailDispatchCache.get(id);
        if (dispatch == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("EmailDispatch not found");
        }
        return ResponseEntity.ok(dispatch);
    }

    // --- Processing methods with real business logic ---

    private void processDigestRequestJob(DigestRequestJob entity) {
        log.info("Processing DigestRequestJob with ID: {}", entity.getId());
        // Update status to PROCESSING
        entity.setStatus(DigestRequestJob.StatusEnum.PROCESSING);
        digestRequestJobCachePut(entity);

        // Simulate data retrieval from petstore API based on metadata or defaults
        // For prototype, create a new DigestData entity with dummy data
        DigestData data = new DigestData();
        data.setJobId(entity.getId());
        data.setData("Sample data from petstore API based on metadata: " + (entity.getMetadata() != null ? entity.getMetadata().toString() : "{}"));
        data.setStatus(DigestData.StatusEnum.RETRIEVED);
        data.setId(String.valueOf(digestDataIdCounter.getAndIncrement()));
        digestDataCachePut(data);
        log.info("Triggered DigestData creation with ID: {}", data.getId());

        // Process DigestData
        processDigestData(data);

        // Update DigestRequestJob status to COMPLETED after successful downstream processing
        entity.setStatus(DigestRequestJob.StatusEnum.COMPLETED);
        digestRequestJobCachePut(entity);
    }

    private void processDigestData(DigestData entity) {
        log.info("Processing DigestData with ID: {}", entity.getId());
        // Format or transform raw data into digest format (e.g. HTML)
        String formattedData = "<html><body><h1>Digest Data</h1><p>" + entity.getData() + "</p></body></html>";
        entity.setData(formattedData);
        entity.setStatus(DigestData.StatusEnum.PROCESSED);
        digestDataCachePut(entity);

        // Create EmailDispatch entity with HTML format
        EmailDispatch dispatch = new EmailDispatch();
        dispatch.setJobId(entity.getJobId());
        dispatch.setEmailFormat(EmailDispatch.EmailFormatEnum.HTML);
        dispatch.setStatus(EmailDispatch.StatusEnum.QUEUED);
        dispatch.setId(String.valueOf(emailDispatchIdCounter.getAndIncrement()));
        emailDispatchCachePut(dispatch);
        log.info("Triggered EmailDispatch creation with ID: {}", dispatch.getId());

        // Process EmailDispatch
        processEmailDispatch(dispatch);
    }

    private void processEmailDispatch(EmailDispatch entity) {
        log.info("Processing EmailDispatch with ID: {}", entity.getId());
        // Simulate sending email (in real implementation, integrate mail service)
        try {
            // Simulate email sending success
            log.info("Sending email for Job ID {} in format {}", entity.getJobId(), entity.getEmailFormat());
            entity.setStatus(EmailDispatch.StatusEnum.SENT);
            emailDispatchCachePut(entity);
            log.info("Email sent successfully for EmailDispatch ID: {}", entity.getId());
        } catch (Exception e) {
            log.error("Failed to send email for EmailDispatch ID: {}", entity.getId(), e);
            entity.setStatus(EmailDispatch.StatusEnum.FAILED);
            emailDispatchCachePut(entity);
        }
    }

    // Helper methods to put entities in caches
    private void digestRequestJobCachePut(DigestRequestJob job) {
        digestRequestJobCache.put(job.getId(), job);
    }
    private void digestDataCachePut(DigestData data) {
        digestDataCache.put(data.getId(), data);
    }
    private void emailDispatchCachePut(EmailDispatch dispatch) {
        emailDispatchCache.put(dispatch.getId(), dispatch);
    }
}