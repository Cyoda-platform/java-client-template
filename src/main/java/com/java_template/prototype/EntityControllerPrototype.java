package com.java_template.prototype;

import com.java_template.application.entity.DigestContent;
import com.java_template.application.entity.DigestJob;
import com.java_template.application.entity.DigestRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, List<DigestJob>> digestJobCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DigestContent>> digestContentCache = new ConcurrentHashMap<>();

    private final AtomicLong digestJobIdCounter = new AtomicLong(1);
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);
    private final AtomicLong digestContentIdCounter = new AtomicLong(1);

    //
    // DigestJob CRUD and event processing
    //

    @PostMapping("/digestjob")
    public ResponseEntity<Map<String, Object>> createDigestJob(@RequestBody @Valid DigestJobRequest digestJobReq) {
        logger.info("Received request to create DigestJob: {}", digestJobReq);

        DigestJob digestJob = new DigestJob();
        digestJob.setId(String.valueOf(digestJobIdCounter.getAndIncrement()));
        digestJob.setTechnicalId(UUID.randomUUID());
        // Map fields from request DTO to entity as needed (none specified, so none mapped)

        if (!digestJob.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestJob entity");
        }

        addDigestJob(digestJob);
        processDigestJob(digestJob);

        logger.info("DigestJob created with id {}", digestJob.getId());
        return ResponseEntity.ok(Map.of("id", digestJob.getId(), "status", "processed"));
    }

    @GetMapping("/digestjob")
    // Example GET with query param using @RequestParam and validation annotation
    public ResponseEntity<DigestJob> getDigestJob(@RequestParam @NotBlank String id) {
        DigestJob job = getDigestJobById(id);
        return ResponseEntity.ok(job);
    }

    @PutMapping("/digestjob")
    public ResponseEntity<Map<String, Object>> updateDigestJob(@RequestBody @Valid DigestJobUpdateRequest digestJobUpdateReq) {
        logger.info("Received request to update DigestJob id {}: {}", digestJobUpdateReq.getId(), digestJobUpdateReq);

        DigestJob existing = getDigestJobById(digestJobUpdateReq.getId());

        DigestJob updated = new DigestJob();
        updated.setId(existing.getId());
        updated.setTechnicalId(existing.getTechnicalId());
        // Map fields from update DTO if any (none specified)

        if (!updated.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestJob entity");
        }

        updateDigestJobInCache(updated.getId(), updated);
        processDigestJob(updated);

        logger.info("DigestJob updated with id {}", updated.getId());
        return ResponseEntity.ok(Map.of("id", updated.getId(), "status", "updated and processed"));
    }

    @DeleteMapping("/digestjob")
    public ResponseEntity<Map<String, String>> deleteDigestJob(@RequestParam @NotBlank String id) {
        logger.info("Received request to delete DigestJob id {}", id);
        boolean removed = deleteDigestJobFromCache(id);
        if (!removed) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestJob not found");
        }
        logger.info("DigestJob deleted with id {}", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
    }

    private void addDigestJob(DigestJob job) {
        digestJobCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(job);
    }

    private DigestJob getDigestJobById(String id) {
        List<DigestJob> jobs = digestJobCache.getOrDefault("entities", Collections.emptyList());
        synchronized (jobs) {
            return jobs.stream()
                    .filter(j -> id.equals(j.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestJob not found"));
        }
    }

    private void updateDigestJobInCache(String id, DigestJob updated) {
        List<DigestJob> jobs = digestJobCache.getOrDefault("entities", Collections.emptyList());
        synchronized (jobs) {
            int idx = -1;
            for (int i = 0; i < jobs.size(); i++) {
                if (id.equals(jobs.get(i).getId())) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestJob not found");
            }
            jobs.set(idx, updated);
        }
    }

    private boolean deleteDigestJobFromCache(String id) {
        List<DigestJob> jobs = digestJobCache.getOrDefault("entities", Collections.emptyList());
        synchronized (jobs) {
            return jobs.removeIf(j -> id.equals(j.getId()));
        }
    }

    private void processDigestJob(DigestJob job) {
        logger.info("Processing DigestJob event for id {}", job.getId());

        List<DigestRequest> requests = digestRequestCache.getOrDefault("entities", Collections.emptyList());
        if (requests.isEmpty()) {
            logger.info("No DigestRequests found to associate with DigestJob id {}", job.getId());
        } else {
            logger.info("Found {} DigestRequests, processing them for DigestJob id {}", requests.size(), job.getId());
            for (DigestRequest req : requests) {
                DigestContent content = new DigestContent();
                content.setId(String.valueOf(digestContentIdCounter.getAndIncrement()));
                content.setTechnicalId(UUID.randomUUID());
                content.setRequestId(req.getId());
                content.setDigestJobId(job.getId());
                content.setContent("Generated content for request " + req.getId());

                addDigestContent(content);
                processDigestContent(content);
            }
        }

        logger.info("DigestJob event processing completed for id {}", job.getId());
    }

    //
    // DigestRequest CRUD and event processing
    //

    @PostMapping("/digestrequest")
    public ResponseEntity<Map<String, Object>> createDigestRequest(@RequestBody @Valid DigestRequestRequest digestRequestReq) {
        logger.info("Received request to create DigestRequest: {}", digestRequestReq);

        DigestRequest request = new DigestRequest();
        request.setId(String.valueOf(digestRequestIdCounter.getAndIncrement()));
        request.setTechnicalId(UUID.randomUUID());
        // Map fields from request DTO to entity if any (none specified)

        if (!request.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestRequest entity");
        }

        addDigestRequest(request);
        processDigestRequest(request);

        logger.info("DigestRequest created with id {}", request.getId());
        return ResponseEntity.ok(Map.of("id", request.getId(), "status", "processed"));
    }

    @GetMapping("/digestrequest")
    public ResponseEntity<DigestRequest> getDigestRequest(@RequestParam @NotBlank String id) {
        DigestRequest req = getDigestRequestById(id);
        return ResponseEntity.ok(req);
    }

    @PutMapping("/digestrequest")
    public ResponseEntity<Map<String, Object>> updateDigestRequest(@RequestBody @Valid DigestRequestUpdateRequest digestRequestUpdateReq) {
        logger.info("Received request to update DigestRequest id {}: {}", digestRequestUpdateReq.getId(), digestRequestUpdateReq);

        DigestRequest existing = getDigestRequestById(digestRequestUpdateReq.getId());

        DigestRequest updated = new DigestRequest();
        updated.setId(existing.getId());
        updated.setTechnicalId(existing.getTechnicalId());
        // Map fields from update DTO if any

        if (!updated.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestRequest entity");
        }

        updateDigestRequestInCache(updated.getId(), updated);
        processDigestRequest(updated);

        logger.info("DigestRequest updated with id {}", updated.getId());
        return ResponseEntity.ok(Map.of("id", updated.getId(), "status", "updated and processed"));
    }

    @DeleteMapping("/digestrequest")
    public ResponseEntity<Map<String, String>> deleteDigestRequest(@RequestParam @NotBlank String id) {
        logger.info("Received request to delete DigestRequest id {}", id);
        boolean removed = deleteDigestRequestFromCache(id);
        if (!removed) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        logger.info("DigestRequest deleted with id {}", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
    }

    private void addDigestRequest(DigestRequest request) {
        digestRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(request);
    }

    private DigestRequest getDigestRequestById(String id) {
        List<DigestRequest> requests = digestRequestCache.getOrDefault("entities", Collections.emptyList());
        synchronized (requests) {
            return requests.stream()
                    .filter(r -> id.equals(r.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestRequest not found"));
        }
    }

    private void updateDigestRequestInCache(String id, DigestRequest updated) {
        List<DigestRequest> requests = digestRequestCache.getOrDefault("entities", Collections.emptyList());
        synchronized (requests) {
            int idx = -1;
            for (int i = 0; i < requests.size(); i++) {
                if (id.equals(requests.get(i).getId())) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestRequest not found");
            }
            requests.set(idx, updated);
        }
    }

    private boolean deleteDigestRequestFromCache(String id) {
        List<DigestRequest> requests = digestRequestCache.getOrDefault("entities", Collections.emptyList());
        synchronized (requests) {
            return requests.removeIf(r -> id.equals(r.getId()));
        }
    }

    private void processDigestRequest(DigestRequest request) {
        logger.info("Processing DigestRequest event for id {}", request.getId());

        List<DigestJob> jobs = digestJobCache.getOrDefault("entities", Collections.emptyList());
        boolean linkedJobFound = false;

        synchronized (jobs) {
            for (DigestJob job : jobs) {
                if (job.getId().equals(request.getId())) {
                    linkedJobFound = true;
                    break;
                }
            }
        }

        if (!linkedJobFound) {
            logger.info("No linked DigestJob found for DigestRequest id {}", request.getId());
        } else {
            logger.info("Linked DigestJob found for DigestRequest id {}", request.getId());
        }
    }

    //
    // DigestContent CRUD and event processing
    //

    @PostMapping("/digestcontent")
    public ResponseEntity<Map<String, Object>> createDigestContent(@RequestBody @Valid DigestContentRequest digestContentReq) {
        logger.info("Received request to create DigestContent: {}", digestContentReq);

        DigestContent content = new DigestContent();
        content.setId(String.valueOf(digestContentIdCounter.getAndIncrement()));
        content.setTechnicalId(UUID.randomUUID());
        content.setContent(digestContentReq.getContent());
        content.setRequestId(digestContentReq.getRequestId()); // added requestId
        content.setDigestJobId(digestContentReq.getDigestJobId());

        if (!content.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestContent entity");
        }

        addDigestContent(content);
        processDigestContent(content);

        logger.info("DigestContent created with id {}", content.getId());
        return ResponseEntity.ok(Map.of("id", content.getId(), "status", "processed"));
    }

    @GetMapping("/digestcontent")
    public ResponseEntity<DigestContent> getDigestContent(@RequestParam @NotBlank String id) {
        DigestContent content = getDigestContentById(id);
        return ResponseEntity.ok(content);
    }

    @PutMapping("/digestcontent")
    public ResponseEntity<Map<String, Object>> updateDigestContent(@RequestBody @Valid DigestContentUpdateRequest digestContentUpdateReq) {
        logger.info("Received request to update DigestContent id {}: {}", digestContentUpdateReq.getId(), digestContentUpdateReq);

        DigestContent existing = getDigestContentById(digestContentUpdateReq.getId());

        DigestContent updated = new DigestContent();
        updated.setId(existing.getId());
        updated.setTechnicalId(existing.getTechnicalId());
        updated.setContent(digestContentUpdateReq.getContent());
        updated.setRequestId(digestContentUpdateReq.getRequestId()); // added requestId
        updated.setDigestJobId(digestContentUpdateReq.getDigestJobId());

        if (!updated.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestContent entity");
        }

        updateDigestContentInCache(updated.getId(), updated);
        processDigestContent(updated);

        logger.info("DigestContent updated with id {}", updated.getId());
        return ResponseEntity.ok(Map.of("id", updated.getId(), "status", "updated and processed"));
    }

    @DeleteMapping("/digestcontent")
    public ResponseEntity<Map<String, String>> deleteDigestContent(@RequestParam @NotBlank String id) {
        logger.info("Received request to delete DigestContent id {}", id);
        boolean removed = deleteDigestContentFromCache(id);
        if (!removed) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestContent not found");
        }
        logger.info("DigestContent deleted with id {}", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
    }

    private void addDigestContent(DigestContent content) {
        digestContentCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(content);
    }

    private DigestContent getDigestContentById(String id) {
        List<DigestContent> contents = digestContentCache.getOrDefault("entities", Collections.emptyList());
        synchronized (contents) {
            return contents.stream()
                    .filter(c -> id.equals(c.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestContent not found"));
        }
    }

    private void updateDigestContentInCache(String id, DigestContent updated) {
        List<DigestContent> contents = digestContentCache.getOrDefault("entities", Collections.emptyList());
        synchronized (contents) {
            int idx = -1;
            for (int i = 0; i < contents.size(); i++) {
                if (id.equals(contents.get(i).getId())) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestContent not found");
            }
            contents.set(idx, updated);
        }
    }

    private boolean deleteDigestContentFromCache(String id) {
        List<DigestContent> contents = digestContentCache.getOrDefault("entities", Collections.emptyList());
        synchronized (contents) {
            return contents.removeIf(c -> id.equals(c.getId()));
        }
    }

    private void processDigestContent(DigestContent content) {
        logger.info("Processing DigestContent event for id {}", content.getId());

        if (content.getContent() == null || content.getContent().isBlank()) {
            logger.warn("DigestContent id {} has empty content field", content.getId());
            // TODO: Possibly trigger reprocessing or error event
        } else {
            logger.info("DigestContent id {} content length: {}", content.getId(), content.getContent().length());
        }

        try {
            DigestRequest linkedRequest = getDigestRequestById(content.getRequestId());
            logger.info("Linked DigestRequest found for DigestContent id {}: requestId {}", content.getId(), linkedRequest.getId());
        } catch (ResponseStatusException ex) {
            logger.warn("Linked DigestRequest id {} not found for DigestContent id {}", content.getRequestId(), content.getId());
        }
    }

    //
    // DTO classes for validation and request bodies
    //

    @Data
    public static class DigestJobRequest {
        // No fields specified for DigestJob in requirements - add if needed
    }

    @Data
    public static class DigestJobUpdateRequest {
        @NotBlank
        private String id;
        // Add other updatable fields as needed
    }

    @Data
    public static class DigestRequestRequest {
        // No fields specified; add if needed
    }

    @Data
    public static class DigestRequestUpdateRequest {
        @NotBlank
        private String id;
        // Add other updatable fields as needed
    }

    @Data
    public static class DigestContentRequest {
        @NotBlank
        private String digestJobId;

        @NotBlank
        private String requestId;

        @NotNull
        @Size(min = 1, max = 10000)
        private String content;
    }

    @Data
    public static class DigestContentUpdateRequest {
        @NotBlank
        private String id;

        @NotBlank
        private String digestJobId;

        @NotBlank
        private String requestId;

        @NotNull
        @Size(min = 1, max = 10000)
        private String content;
    }

}