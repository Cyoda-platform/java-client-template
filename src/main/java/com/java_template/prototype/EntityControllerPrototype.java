package com.java_template.prototype;

import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.application.entity.Job;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/prototype/entity")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // Caches for entities
    private final ConcurrentHashMap<String, List<Job>> jobCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<EmailDispatch>> emailDispatchCache = new ConcurrentHashMap<>();

    // ID counters (to simulate DB generated IDs)
    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    // ======== JOB CRUD ========

    @PostMapping("/job")
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobCreateDto jobDto) {
        try {
            Job job = toJobEntity(jobDto);
            String id = addJob(job);
            logger.info("Created Job with id {}", id);
            return ResponseEntity.ok(Map.of("id", id, "status", "Job processed"));
        } catch (Exception e) {
            logger.error("Error creating Job", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error creating Job");
        }
    }

    @GetMapping("/job")
    public ResponseEntity<Job> getJob(@RequestParam @NotBlank String id) {
        Job job = getJobById(id);
        if (job == null) {
            logger.info("Job with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(job);
    }

    @PutMapping("/job")
    public ResponseEntity<Job> updateJob(@RequestBody @Valid JobUpdateDto jobDto) {
        if(jobDto.getId() == null || jobDto.getId().isBlank()){
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Job id must be provided");
        }
        Job updatedJob = toJobEntity(jobDto);
        Job job = updateJobById(jobDto.getId(), updatedJob);
        if (job == null) {
            logger.info("Job with id {} not found for update", jobDto.getId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        logger.info("Updated Job with id {}", jobDto.getId());
        return ResponseEntity.ok(job);
    }

    @DeleteMapping("/job")
    public ResponseEntity<Map<String, String>> deleteJob(@RequestParam @NotBlank String id) {
        boolean deleted = deleteJobById(id);
        if (!deleted) {
            logger.info("Job with id {} not found for delete", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        logger.info("Deleted Job with id {}", id);
        return ResponseEntity.ok(Map.of("status", "Job deleted"));
    }

    // ======== DIGEST REQUEST CRUD ========

    @PostMapping("/digestRequest")
    public ResponseEntity<Map<String, Object>> createDigestRequest(@RequestBody @Valid DigestRequestCreateDto dto) {
        try {
            DigestRequest request = toDigestRequestEntity(dto);
            String id = addDigestRequest(request);
            logger.info("Created DigestRequest with id {}", id);
            return ResponseEntity.ok(Map.of("id", id, "status", "DigestRequest processed"));
        } catch (Exception e) {
            logger.error("Error creating DigestRequest", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error creating DigestRequest");
        }
    }

    @GetMapping("/digestRequest")
    public ResponseEntity<DigestRequest> getDigestRequest(@RequestParam @NotBlank String id) {
        DigestRequest request = getDigestRequestById(id);
        if (request == null) {
            logger.info("DigestRequest with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        return ResponseEntity.ok(request);
    }

    @PutMapping("/digestRequest")
    public ResponseEntity<DigestRequest> updateDigestRequest(@RequestBody @Valid DigestRequestUpdateDto dto) {
        if(dto.getId() == null || dto.getId().isBlank()){
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "DigestRequest id must be provided");
        }
        DigestRequest updatedRequest = toDigestRequestEntity(dto);
        DigestRequest request = updateDigestRequestById(dto.getId(), updatedRequest);
        if (request == null) {
            logger.info("DigestRequest with id {} not found for update", dto.getId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        logger.info("Updated DigestRequest with id {}", dto.getId());
        return ResponseEntity.ok(request);
    }

    @DeleteMapping("/digestRequest")
    public ResponseEntity<Map<String, String>> deleteDigestRequest(@RequestParam @NotBlank String id) {
        boolean deleted = deleteDigestRequestById(id);
        if (!deleted) {
            logger.info("DigestRequest with id {} not found for delete", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        logger.info("Deleted DigestRequest with id {}", id);
        return ResponseEntity.ok(Map.of("status", "DigestRequest deleted"));
    }

    // ======== EMAIL DISPATCH CRUD ========

    @PostMapping("/emailDispatch")
    public ResponseEntity<Map<String, Object>> createEmailDispatch(@RequestBody @Valid EmailDispatchCreateDto dto) {
        try {
            EmailDispatch dispatch = toEmailDispatchEntity(dto);
            String id = addEmailDispatch(dispatch);
            logger.info("Created EmailDispatch with id {}", id);
            return ResponseEntity.ok(Map.of("id", id, "status", "EmailDispatch processed"));
        } catch (Exception e) {
            logger.error("Error creating EmailDispatch", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error creating EmailDispatch");
        }
    }

    @GetMapping("/emailDispatch")
    public ResponseEntity<EmailDispatch> getEmailDispatch(@RequestParam @NotBlank String id) {
        EmailDispatch dispatch = getEmailDispatchById(id);
        if (dispatch == null) {
            logger.info("EmailDispatch with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "EmailDispatch not found");
        }
        return ResponseEntity.ok(dispatch);
    }

    @PutMapping("/emailDispatch")
    public ResponseEntity<EmailDispatch> updateEmailDispatch(@RequestBody @Valid EmailDispatchUpdateDto dto) {
        if(dto.getId() == null || dto.getId().isBlank()){
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "EmailDispatch id must be provided");
        }
        EmailDispatch updatedDispatch = toEmailDispatchEntity(dto);
        EmailDispatch dispatch = updateEmailDispatchById(dto.getId(), updatedDispatch);
        if (dispatch == null) {
            logger.info("EmailDispatch with id {} not found for update", dto.getId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "EmailDispatch not found");
        }
        logger.info("Updated EmailDispatch with id {}", dto.getId());
        return ResponseEntity.ok(dispatch);
    }

    @DeleteMapping("/emailDispatch")
    public ResponseEntity<Map<String, String>> deleteEmailDispatch(@RequestParam @NotBlank String id) {
        boolean deleted = deleteEmailDispatchById(id);
        if (!deleted) {
            logger.info("EmailDispatch with id {} not found for delete", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "EmailDispatch not found");
        }
        logger.info("Deleted EmailDispatch with id {}", id);
        return ResponseEntity.ok(Map.of("status", "EmailDispatch deleted"));
    }

    // ======= Cache & Event Processing Methods =======

    // --- Job ---

    private String addJob(Job job) {
        String id = String.valueOf(jobIdCounter.getAndIncrement());
        job.setId(id);
        jobCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(job);
        processJob(job);
        return id;
    }

    private Job getJobById(String id) {
        List<Job> jobs = jobCache.get("entities");
        if (jobs == null) return null;
        synchronized (jobs) {
            return jobs.stream().filter(j -> id.equals(j.getId())).findFirst().orElse(null);
        }
    }

    private Job updateJobById(String id, Job updatedJob) {
        List<Job> jobs = jobCache.get("entities");
        if (jobs == null) return null;
        synchronized (jobs) {
            for (int i = 0; i < jobs.size(); i++) {
                if (id.equals(jobs.get(i).getId())) {
                    updatedJob.setId(id);
                    jobs.set(i, updatedJob);
                    processJob(updatedJob);
                    return updatedJob;
                }
            }
        }
        return null;
    }

    private boolean deleteJobById(String id) {
        List<Job> jobs = jobCache.get("entities");
        if (jobs == null) return false;
        synchronized (jobs) {
            return jobs.removeIf(j -> id.equals(j.getId()));
        }
    }

    private void processJob(Job job) {
        // TODO: Replace with real Cyoda event processing logic
        logger.info("Processing Job entity (simulated event): {}", job.getId());
    }

    // --- DigestRequest ---

    private String addDigestRequest(DigestRequest request) {
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        request.setId(id);
        digestRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(request);
        processDigestRequest(request);
        return id;
    }

    private DigestRequest getDigestRequestById(String id) {
        List<DigestRequest> requests = digestRequestCache.get("entities");
        if (requests == null) return null;
        synchronized (requests) {
            return requests.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
        }
    }

    private DigestRequest updateDigestRequestById(String id, DigestRequest updatedRequest) {
        List<DigestRequest> requests = digestRequestCache.get("entities");
        if (requests == null) return null;
        synchronized (requests) {
            for (int i = 0; i < requests.size(); i++) {
                if (id.equals(requests.get(i).getId())) {
                    updatedRequest.setId(id);
                    requests.set(i, updatedRequest);
                    processDigestRequest(updatedRequest);
                    return updatedRequest;
                }
            }
        }
        return null;
    }

    private boolean deleteDigestRequestById(String id) {
        List<DigestRequest> requests = digestRequestCache.get("entities");
        if (requests == null) return false;
        synchronized (requests) {
            return requests.removeIf(r -> id.equals(r.getId()));
        }
    }

    private void processDigestRequest(DigestRequest request) {
        // TODO: Replace with real Cyoda event processing logic
        logger.info("Processing DigestRequest entity (simulated event): {}", request.getId());
    }

    // --- EmailDispatch ---

    private String addEmailDispatch(EmailDispatch dispatch) {
        String id = String.valueOf(emailDispatchIdCounter.getAndIncrement());
        dispatch.setId(id);
        emailDispatchCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(dispatch);
        processEmailDispatch(dispatch);
        return id;
    }

    private EmailDispatch getEmailDispatchById(String id) {
        List<EmailDispatch> dispatches = emailDispatchCache.get("entities");
        if (dispatches == null) return null;
        synchronized (dispatches) {
            return dispatches.stream().filter(d -> id.equals(d.getId())).findFirst().orElse(null);
        }
    }

    private EmailDispatch updateEmailDispatchById(String id, EmailDispatch updatedDispatch) {
        List<EmailDispatch> dispatches = emailDispatchCache.get("entities");
        if (dispatches == null) return null;
        synchronized (dispatches) {
            for (int i = 0; i < dispatches.size(); i++) {
                if (id.equals(dispatches.get(i).getId())) {
                    updatedDispatch.setId(id);
                    dispatches.set(i, updatedDispatch);
                    processEmailDispatch(updatedDispatch);
                    return updatedDispatch;
                }
            }
        }
        return null;
    }

    private boolean deleteEmailDispatchById(String id) {
        List<EmailDispatch> dispatches = emailDispatchCache.get("entities");
        if (dispatches == null) return false;
        synchronized (dispatches) {
            return dispatches.removeIf(d -> id.equals(d.getId()));
        }
    }

    private void processEmailDispatch(EmailDispatch dispatch) {
        // TODO: Replace with real Cyoda event processing logic
        logger.info("Processing EmailDispatch entity (simulated event): {}", dispatch.getId());
    }

    // ======= DTOs =======

    @Data
    public static class JobCreateDto {
        @NotBlank
        private String technicalId;
        @NotBlank
        @Size(max = 255)
        private String name;
        @NotNull
        private Boolean active;
        // Additional fields can be added as needed
    }

    @Data
    public static class JobUpdateDto extends JobCreateDto {
        @NotBlank
        private String id;
    }

    @Data
    public static class DigestRequestCreateDto {
        @NotBlank
        private String technicalId;
        @NotBlank
        private String userId;
        @NotBlank
        @Size(max = 255)
        private String content;
        @NotBlank
        @Pattern(regexp = "DAILY|WEEKLY|MONTHLY")
        private String frequency;
        // Additional fields as needed
    }

    @Data
    public static class DigestRequestUpdateDto extends DigestRequestCreateDto {
        @NotBlank
        private String id;
    }

    @Data
    public static class EmailDispatchCreateDto {
        @NotBlank
        private String technicalId;
        @NotBlank
        private String digestRequestId;
        @NotBlank
        @Size(max = 320) // typical max email length
        private String recipientEmail;
        @NotBlank
        @Pattern(regexp = "PENDING|SENT|FAILED")
        private String status;
        // Additional fields as needed
    }

    @Data
    public static class EmailDispatchUpdateDto extends EmailDispatchCreateDto {
        @NotBlank
        private String id;
    }

    // ======= Converters from DTO to Entity =======

    private Job toJobEntity(JobCreateDto dto) {
        Job job = new Job();
        job.setTechnicalId(dto.getTechnicalId());
        job.setName(dto.getName());
        job.setActive(dto.getActive());
        return job;
    }

    private Job toJobEntity(JobUpdateDto dto) {
        Job job = toJobEntity((JobCreateDto) dto);
        job.setId(dto.getId());
        return job;
    }

    private DigestRequest toDigestRequestEntity(DigestRequestCreateDto dto) {
        DigestRequest request = new DigestRequest();
        request.setTechnicalId(dto.getTechnicalId());
        request.setUserId(dto.getUserId());
        request.setContent(dto.getContent());
        request.setFrequency(dto.getFrequency());
        return request;
    }

    private DigestRequest toDigestRequestEntity(DigestRequestUpdateDto dto) {
        DigestRequest request = toDigestRequestEntity((DigestRequestCreateDto) dto);
        request.setId(dto.getId());
        return request;
    }

    private EmailDispatch toEmailDispatchEntity(EmailDispatchCreateDto dto) {
        EmailDispatch dispatch = new EmailDispatch();
        dispatch.setTechnicalId(dto.getTechnicalId());
        dispatch.setDigestRequestId(dto.getDigestRequestId());
        dispatch.setRecipientEmail(dto.getRecipientEmail());
        dispatch.setStatus(dto.getStatus());
        return dispatch;
    }

    private EmailDispatch toEmailDispatchEntity(EmailDispatchUpdateDto dto) {
        EmailDispatch dispatch = toEmailDispatchEntity((EmailDispatchCreateDto) dto);
        dispatch.setId(dto.getId());
        return dispatch;
    }

}
```