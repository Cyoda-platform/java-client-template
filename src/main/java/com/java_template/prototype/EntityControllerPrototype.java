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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Validated
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, List<Job>> jobCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<EmailDispatch>> emailDispatchCache = new ConcurrentHashMap<>();

    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    @PostConstruct
    public void init() {
        jobCache.put("entities", new ArrayList<>());
        digestRequestCache.put("entities", new ArrayList<>());
        emailDispatchCache.put("entities", new ArrayList<>());
    }

    // ======== JOB CRUD ========

    @PostMapping("/jobs")
    public ResponseEntity<CreateResponse> createJob(@RequestBody @Valid JobCreateUpdateDTO jobDto) {
        logger.info("Received request to create Job: {}", jobDto);
        Job job = toJob(jobDto);
        if (!job.isValid()) {
            logger.error("Invalid Job entity received");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Job entity");
        }
        String id = addJob(job);
        logger.info("Job created with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateResponse(id, "Job created and processed"));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<Job>> listJobs() {
        logger.info("Listing all Jobs");
        return ResponseEntity.ok(jobCache.getOrDefault("entities", List.of()));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable @NotBlank String id) {
        logger.info("Fetching Job with ID: {}", id);
        Job found = getJobById(id);
        if (found == null) {
            logger.error("Job not found with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(found);
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<CreateResponse> updateJob(@PathVariable @NotBlank String id,
                                                    @RequestBody @Valid JobCreateUpdateDTO jobDto) {
        logger.info("Updating Job with ID: {}", id);
        Job job = toJob(jobDto);
        if (!job.isValid()) {
            logger.error("Invalid Job entity received for update");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Job entity");
        }
        boolean updated = updateJobById(id, job);
        if (!updated) {
            logger.error("Job not found for update with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        processJob(job);
        return ResponseEntity.ok(new CreateResponse(id, "Job updated and processed"));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<CreateResponse> deleteJob(@PathVariable @NotBlank String id) {
        logger.info("Deleting Job with ID: {}", id);
        boolean deleted = deleteJobById(id);
        if (!deleted) {
            logger.error("Job not found for deletion with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        return ResponseEntity.ok(new CreateResponse(id, "Job deleted"));
    }

    // ======== DIGESTREQUEST CRUD ========

    @PostMapping("/digestRequests")
    public ResponseEntity<CreateResponse> createDigestRequest(@RequestBody @Valid DigestRequestCreateUpdateDTO drDto) {
        logger.info("Received request to create DigestRequest: {}", drDto);
        DigestRequest dr = toDigestRequest(drDto);
        if (!dr.isValid()) {
            logger.error("Invalid DigestRequest entity received");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest entity");
        }
        String id = addDigestRequest(dr);
        logger.info("DigestRequest created with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateResponse(id, "DigestRequest created and processed"));
    }

    @GetMapping("/digestRequests")
    public ResponseEntity<List<DigestRequest>> listDigestRequests() {
        logger.info("Listing all DigestRequests");
        return ResponseEntity.ok(digestRequestCache.getOrDefault("entities", List.of()));
    }

    @GetMapping("/digestRequests/{id}")
    public ResponseEntity<DigestRequest> getDigestRequest(@PathVariable @NotBlank String id) {
        logger.info("Fetching DigestRequest with ID: {}", id);
        DigestRequest found = getDigestRequestById(id);
        if (found == null) {
            logger.error("DigestRequest not found with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        return ResponseEntity.ok(found);
    }

    @PutMapping("/digestRequests/{id}")
    public ResponseEntity<CreateResponse> updateDigestRequest(@PathVariable @NotBlank String id,
                                                              @RequestBody @Valid DigestRequestCreateUpdateDTO drDto) {
        logger.info("Updating DigestRequest with ID: {}", id);
        DigestRequest dr = toDigestRequest(drDto);
        if (!dr.isValid()) {
            logger.error("Invalid DigestRequest entity received for update");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest entity");
        }
        boolean updated = updateDigestRequestById(id, dr);
        if (!updated) {
            logger.error("DigestRequest not found for update with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        processDigestRequest(dr);
        return ResponseEntity.ok(new CreateResponse(id, "DigestRequest updated and processed"));
    }

    @DeleteMapping("/digestRequests/{id}")
    public ResponseEntity<CreateResponse> deleteDigestRequest(@PathVariable @NotBlank String id) {
        logger.info("Deleting DigestRequest with ID: {}", id);
        boolean deleted = deleteDigestRequestById(id);
        if (!deleted) {
            logger.error("DigestRequest not found for deletion with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        return ResponseEntity.ok(new CreateResponse(id, "DigestRequest deleted"));
    }

    // ======== EMAILDISPATCH CRUD ========

    @PostMapping("/emailDispatches")
    public ResponseEntity<CreateResponse> createEmailDispatch(@RequestBody @Valid EmailDispatchCreateUpdateDTO edDto) {
        logger.info("Received request to create EmailDispatch: {}", edDto);
        EmailDispatch ed = toEmailDispatch(edDto);
        if (!ed.isValid()) {
            logger.error("Invalid EmailDispatch entity received");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid EmailDispatch entity");
        }
        String id = addEmailDispatch(ed);
        logger.info("EmailDispatch created with ID: {}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateResponse(id, "EmailDispatch created and processed"));
    }

    @GetMapping("/emailDispatches")
    public ResponseEntity<List<EmailDispatch>> listEmailDispatches() {
        logger.info("Listing all EmailDispatches");
        return ResponseEntity.ok(emailDispatchCache.getOrDefault("entities", List.of()));
    }

    @GetMapping("/emailDispatches/{id}")
    public ResponseEntity<EmailDispatch> getEmailDispatch(@PathVariable @NotBlank String id) {
        logger.info("Fetching EmailDispatch with ID: {}", id);
        EmailDispatch found = getEmailDispatchById(id);
        if (found == null) {
            logger.error("EmailDispatch not found with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found");
        }
        return ResponseEntity.ok(found);
    }

    @PutMapping("/emailDispatches/{id}")
    public ResponseEntity<CreateResponse> updateEmailDispatch(@PathVariable @NotBlank String id,
                                                              @RequestBody @Valid EmailDispatchCreateUpdateDTO edDto) {
        logger.info("Updating EmailDispatch with ID: {}", id);
        EmailDispatch ed = toEmailDispatch(edDto);
        if (!ed.isValid()) {
            logger.error("Invalid EmailDispatch entity received for update");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid EmailDispatch entity");
        }
        boolean updated = updateEmailDispatchById(id, ed);
        if (!updated) {
            logger.error("EmailDispatch not found for update with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found");
        }
        processEmailDispatch(ed);
        return ResponseEntity.ok(new CreateResponse(id, "EmailDispatch updated and processed"));
    }

    @DeleteMapping("/emailDispatches/{id}")
    public ResponseEntity<CreateResponse> deleteEmailDispatch(@PathVariable @NotBlank String id) {
        logger.info("Deleting EmailDispatch with ID: {}", id);
        boolean deleted = deleteEmailDispatchById(id);
        if (!deleted) {
            logger.error("EmailDispatch not found for deletion with ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found");
        }
        return ResponseEntity.ok(new CreateResponse(id, "EmailDispatch deleted"));
    }

    // ======= CACHE & PROCESSING METHODS =======

    private String addJob(Job job) {
        String id = String.valueOf(jobIdCounter.getAndIncrement());
        job.setId(id);
        job.setTechnicalId(UUID.randomUUID());
        jobCache.computeIfAbsent("entities", k -> new ArrayList<>()).add(job);
        logger.info("Added Job to cache: {}", job);
        processJob(job);
        return id;
    }

    private Job getJobById(String id) {
        return jobCache.getOrDefault("entities", List.of()).stream()
                .filter(j -> id.equals(j.getId()))
                .findFirst()
                .orElse(null);
    }

    private boolean updateJobById(String id, Job job) {
        List<Job> list = jobCache.get("entities");
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).getId())) {
                job.setId(id);
                job.setTechnicalId(list.get(i).getTechnicalId());
                list.set(i, job);
                logger.info("Updated Job in cache: {}", job);
                return true;
            }
        }
        return false;
    }

    private boolean deleteJobById(String id) {
        List<Job> list = jobCache.get("entities");
        if (list == null) return false;
        return list.removeIf(j -> id.equals(j.getId()));
    }

    private void processJob(Job job) {
        // TODO: Replace this mock processing with real Cyoda event-driven logic
        logger.info("Processing Job entity with id={} and name={}", job.getId(), job.getName());
    }

    private String addDigestRequest(DigestRequest dr) {
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        dr.setId(id);
        dr.setTechnicalId(UUID.randomUUID());
        digestRequestCache.computeIfAbsent("entities", k -> new ArrayList<>()).add(dr);
        logger.info("Added DigestRequest to cache: {}", dr);
        processDigestRequest(dr);
        return id;
    }

    private DigestRequest getDigestRequestById(String id) {
        return digestRequestCache.getOrDefault("entities", List.of()).stream()
                .filter(d -> id.equals(d.getId()))
                .findFirst()
                .orElse(null);
    }

    private boolean updateDigestRequestById(String id, DigestRequest dr) {
        List<DigestRequest> list = digestRequestCache.get("entities");
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).getId())) {
                dr.setId(id);
                dr.setTechnicalId(list.get(i).getTechnicalId());
                list.set(i, dr);
                logger.info("Updated DigestRequest in cache: {}", dr);
                return true;
            }
        }
        return false;
    }

    private boolean deleteDigestRequestById(String id) {
        List<DigestRequest> list = digestRequestCache.get("entities");
        if (list == null) return false;
        return list.removeIf(d -> id.equals(d.getId()));
    }

    private void processDigestRequest(DigestRequest dr) {
        // TODO: Replace this mock processing with real Cyoda event-driven logic
        logger.info("Processing DigestRequest entity with id={} and userId={}", dr.getId(), dr.getUserId());
    }

    private String addEmailDispatch(EmailDispatch ed) {
        String id = String.valueOf(emailDispatchIdCounter.getAndIncrement());
        ed.setId(id);
        ed.setTechnicalId(UUID.randomUUID());
        emailDispatchCache.computeIfAbsent("entities", k -> new ArrayList<>()).add(ed);
        logger.info("Added EmailDispatch to cache: {}", ed);
        processEmailDispatch(ed);
        return id;
    }

    private EmailDispatch getEmailDispatchById(String id) {
        return emailDispatchCache.getOrDefault("entities", List.of()).stream()
                .filter(e -> id.equals(e.getId()))
                .findFirst()
                .orElse(null);
    }

    private boolean updateEmailDispatchById(String id, EmailDispatch ed) {
        List<EmailDispatch> list = emailDispatchCache.get("entities");
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.get(i).getId())) {
                ed.setId(id);
                ed.setTechnicalId(list.get(i).getTechnicalId());
                list.set(i, ed);
                logger.info("Updated EmailDispatch in cache: {}", ed);
                return true;
            }
        }
        return false;
    }

    private boolean deleteEmailDispatchById(String id) {
        List<EmailDispatch> list = emailDispatchCache.get("entities");
        if (list == null) return false;
        return list.removeIf(e -> id.equals(e.getId()));
    }

    private void processEmailDispatch(EmailDispatch ed) {
        // TODO: Replace this mock processing with real Cyoda event-driven logic
        logger.info("Processing EmailDispatch entity with id={} and email={}", ed.getId(), ed.getEmail());
    }

    // ======== DTO classes for validation & mapping ========

    @Data
    public static class JobCreateUpdateDTO {
        @NotBlank
        @Size(max = 255)
        private String name;

        @NotBlank
        @Size(max = 255)
        private String description;

        @NotNull
        private Integer priority;
    }

    private Job toJob(JobCreateUpdateDTO dto) {
        Job job = new Job();
        job.setName(dto.getName());
        job.setDescription(dto.getDescription());
        job.setPriority(dto.getPriority());
        return job;
    }

    @Data
    public static class DigestRequestCreateUpdateDTO {
        @NotBlank
        @Size(max = 36)
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "userId must be a UUID string")
        private String userId;

        @NotBlank
        @Size(max = 255)
        private String digestType;

        @NotNull
        private Long timestamp;
    }

    private DigestRequest toDigestRequest(DigestRequestCreateUpdateDTO dto) {
        DigestRequest dr = new DigestRequest();
        dr.setUserId(dto.getUserId());
        dr.setDigestType(dto.getDigestType());
        dr.setTimestamp(dto.getTimestamp());
        return dr;
    }

    @Data
    public static class EmailDispatchCreateUpdateDTO {
        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,6}$", message = "invalid email format")
        private String email;

        @NotBlank
        @Size(max = 1000)
        private String content;

        @NotNull
        private Boolean sent;
    }

    private EmailDispatch toEmailDispatch(EmailDispatchCreateUpdateDTO dto) {
        EmailDispatch ed = new EmailDispatch();
        ed.setEmail(dto.getEmail());
        ed.setContent(dto.getContent());
        ed.setSent(dto.getSent());
        return ed;
    }

    @Data
    private static class CreateResponse {
        private final String entityId;
        private final String status;
    }
}