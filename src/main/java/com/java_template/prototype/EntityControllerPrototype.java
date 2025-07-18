```java
package com.java_template.prototype;

import com.java_template.application.entity.DigestContent;
import com.java_template.application.entity.DigestJob;
import com.java_template.application.entity.DigestRequest;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prototype REST controller for managing DigestJob, DigestRequest, DigestContent entities.
 * 
 * Implements:
 * - Thread-safe in-memory caches via ConcurrentHashMap with List<Entity> for CRUD simulation
 * - Event-driven pattern: after entity creation/update, processEntity method is called to simulate Cyoda event processing
 * - Basic validation & logging
 *
 * Base path prefix: /prototype
 */
@Slf4j
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    // Caches for entities keyed by "entities"
    private final ConcurrentHashMap<String, List<DigestJob>> digestJobCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<DigestContent>> digestContentCache = new ConcurrentHashMap<>();

    // Atomic counters for generating incremental business IDs (as Strings)
    private final AtomicLong digestJobIdCounter = new AtomicLong(1);
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);
    private final AtomicLong digestContentIdCounter = new AtomicLong(1);

    //
    // region DigestJob CRUD and event processing
    //

    @PostMapping("/digestjob")
    public Map<String, Object> createDigestJob(@RequestBody DigestJob digestJob) {
        log.info("Received request to create DigestJob: {}", digestJob);

        if (!digestJob.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestJob entity");
        }

        // Generate unique IDs
        digestJob.setId(String.valueOf(digestJobIdCounter.getAndIncrement()));
        digestJob.setTechnicalId(UUID.randomUUID());

        addDigestJob(digestJob);
        processDigestJob(digestJob);

        log.info("DigestJob created with id {}", digestJob.getId());
        return Map.of("id", digestJob.getId(), "status", "processed");
    }

    @GetMapping("/digestjob/{id}")
    public DigestJob getDigestJob(@PathVariable String id) {
        return getDigestJobById(id);
    }

    @PutMapping("/digestjob/{id}")
    public Map<String, Object> updateDigestJob(@PathVariable String id, @RequestBody DigestJob updated) {
        log.info("Received request to update DigestJob id {}: {}", id, updated);
        DigestJob existing = getDigestJobById(id);

        if (!updated.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestJob entity");
        }

        // Update fields except IDs
        updated.setId(existing.getId());
        updated.setTechnicalId(existing.getTechnicalId());

        updateDigestJobInCache(id, updated);
        processDigestJob(updated);

        log.info("DigestJob updated with id {}", id);
        return Map.of("id", id, "status", "updated and processed");
    }

    @DeleteMapping("/digestjob/{id}")
    public Map<String, String> deleteDigestJob(@PathVariable String id) {
        log.info("Received request to delete DigestJob id {}", id);
        boolean removed = deleteDigestJobFromCache(id);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestJob not found");
        }
        log.info("DigestJob deleted with id {}", id);
        return Map.of("id", id, "status", "deleted");
    }

    //
    // region DigestJob Cache operations
    //

    private void addDigestJob(DigestJob job) {
        digestJobCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(job);
    }

    private DigestJob getDigestJobById(String id) {
        List<DigestJob> jobs = digestJobCache.getOrDefault("entities", Collections.emptyList());
        synchronized (jobs) {
            return jobs.stream()
                    .filter(j -> id.equals(j.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestJob not found"));
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
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestJob not found");
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

    /**
     * Simulated event processor for DigestJob entity.
     * This method imitates Cyoda event processing by:
     * - Logging the event
     * - Checking if a DigestJob references any DigestRequest IDs and loads them from cache
     * - Orchestrating creation of DigestContent entities based on DigestRequests (mocked logic)
     */
    private void processDigestJob(DigestJob job) {
        log.info("Processing DigestJob event for id {}", job.getId());

        // TODO: Replace below placeholders with real business logic as per actual requirements

        // Example: Load related DigestRequests to this job if any (assuming job has a list of request IDs)
        // Since DigestJob fields are not specified, simulate loading all DigestRequests
        List<DigestRequest> requests = digestRequestCache.getOrDefault("entities", Collections.emptyList());
        if (requests.isEmpty()) {
            log.info("No DigestRequests found to associate with DigestJob id {}", job.getId());
        } else {
            log.info("Found {} DigestRequests, processing them for DigestJob id {}", requests.size(), job.getId());
            // For each DigestRequest, create DigestContent entities to simulate orchestration
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

        log.info("DigestJob event processing completed for id {}", job.getId());
    }

    //
    // endregion DigestJob
    //


    //
    // region DigestRequest CRUD and event processing
    //

    @PostMapping("/digestrequest")
    public Map<String, Object> createDigestRequest(@RequestBody DigestRequest request) {
        log.info("Received request to create DigestRequest: {}", request);

        if (!request.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest entity");
        }

        request.setId(String.valueOf(digestRequestIdCounter.getAndIncrement()));
        request.setTechnicalId(UUID.randomUUID());

        addDigestRequest(request);
        processDigestRequest(request);

        log.info("DigestRequest created with id {}", request.getId());
        return Map.of("id", request.getId(), "status", "processed");
    }

    @GetMapping("/digestrequest/{id}")
    public DigestRequest getDigestRequest(@PathVariable String id) {
        return getDigestRequestById(id);
    }

    @PutMapping("/digestrequest/{id}")
    public Map<String, Object> updateDigestRequest(@PathVariable String id, @RequestBody DigestRequest updated) {
        log.info("Received request to update DigestRequest id {}: {}", id, updated);
        DigestRequest existing = getDigestRequestById(id);

        if (!updated.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest entity");
        }

        updated.setId(existing.getId());
        updated.setTechnicalId(existing.getTechnicalId());

        updateDigestRequestInCache(id, updated);
        processDigestRequest(updated);

        log.info("DigestRequest updated with id {}", id);
        return Map.of("id", id, "status", "updated and processed");
    }

    @DeleteMapping("/digestrequest/{id}")
    public Map<String, String> deleteDigestRequest(@PathVariable String id) {
        log.info("Received request to delete DigestRequest id {}", id);
        boolean removed = deleteDigestRequestFromCache(id);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        log.info("DigestRequest deleted with id {}", id);
        return Map.of("id", id, "status", "deleted");
    }

    //
    // region DigestRequest Cache operations
    //

    private void addDigestRequest(DigestRequest request) {
        digestRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(request);
    }

    private DigestRequest getDigestRequestById(String id) {
        List<DigestRequest> requests = digestRequestCache.getOrDefault("entities", Collections.emptyList());
        synchronized (requests) {
            return requests.stream()
                    .filter(r -> id.equals(r.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found"));
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
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
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

    /**
     * Simulated event processor for DigestRequest entity.
     * Mimics Cyoda event processing by:
     * - Logging
     * - Possibly triggering additional orchestration such as linking with DigestJob or validating content
     * 
     * Here, we just log and simulate a simple validation.
     */
    private void processDigestRequest(DigestRequest request) {
        log.info("Processing DigestRequest event for id {}", request.getId());

        // TODO: Add real business logic or orchestration here
        // Example: Check if this request is linked to a DigestJob (mock check)
        List<DigestJob> jobs = digestJobCache.getOrDefault("entities", Collections.emptyList());
        boolean linkedJobFound = false;

        synchronized (jobs) {
            for (DigestJob job : jobs) {
                // Placeholder logic: if job.id equals request.id (unlikely), consider linked
                if (job.getId().equals(request.getId())) {
                    linkedJobFound = true;
                    break;
                }
            }
        }

        if (!linkedJobFound) {
            log.info("No linked DigestJob found for DigestRequest id {}", request.getId());
        } else {
            log.info("Linked DigestJob found for DigestRequest id {}", request.getId());
        }
    }

    //
    // endregion DigestRequest
    //


    //
    // region DigestContent CRUD and event processing
    //

    @PostMapping("/digestcontent")
    public Map<String, Object> createDigestContent(@RequestBody DigestContent content) {
        log.info("Received request to create DigestContent: {}", content);

        if (!content.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestContent entity");
        }

        content.setId(String.valueOf(digestContentIdCounter.getAndIncrement()));
        content.setTechnicalId(UUID.randomUUID());

        addDigestContent(content);
        processDigestContent(content);

        log.info("DigestContent created with id {}", content.getId());
        return Map.of("id", content.getId(), "status", "processed");
    }

    @GetMapping("/digestcontent/{id}")
    public DigestContent getDigestContent(@PathVariable String id) {
        return getDigestContentById(id);
    }

    @PutMapping("/digestcontent/{id}")
    public Map<String, Object> updateDigestContent(@PathVariable String id, @RequestBody DigestContent updated) {
        log.info("Received request to update DigestContent id {}: {}", id, updated);
        DigestContent existing = getDigestContentById(id);

        if (!updated.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestContent entity");
        }

        updated.setId(existing.getId());
        updated.setTechnicalId(existing.getTechnicalId());

        updateDigestContentInCache(id, updated);
        processDigestContent(updated);

        log.info("DigestContent updated with id {}", id);
        return Map.of("id", id, "status", "updated and processed");
    }

    @DeleteMapping("/digestcontent/{id}")
    public Map<String, String> deleteDigestContent(@PathVariable String id) {
        log.info("Received request to delete DigestContent id {}", id);
        boolean removed = deleteDigestContentFromCache(id);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestContent not found");
        }
        log.info("DigestContent deleted with id {}", id);
        return Map.of("id", id, "status", "deleted");
    }

    //
    // region DigestContent Cache operations
    //

    private void addDigestContent(DigestContent content) {
        digestContentCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(content);
    }

    private DigestContent getDigestContentById(String id) {
        List<DigestContent> contents = digestContentCache.getOrDefault("entities", Collections.emptyList());
        synchronized (contents) {
            return contents.stream()
                    .filter(c -> id.equals(c.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestContent not found"));
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
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestContent not found");
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

    /**
     * Simulated event processor for DigestContent entity.
     * Possible business logic:
     * - Validate content length or format
     * - Link back to DigestRequest or DigestJob and update status
     * - Log summary info
     */
    private void processDigestContent(DigestContent content) {
        log.info("Processing DigestContent event for id {}", content.getId());

        if (content.getContent() == null || content.getContent().isBlank()) {
            log.warn("DigestContent id {} has empty content field", content.getId());
            // TODO: Possibly trigger reprocessing or error event
        } else {
            log.info("DigestContent id {} content length: {}", content.getId(), content.getContent().length());
        }

        // Example: check if linked DigestRequest exists
        try {
            DigestRequest linkedRequest = getDigestRequestById(content.getRequestId());
            log.info("Linked DigestRequest found for DigestContent id {}: requestId {}", content.getId(), linkedRequest.getId());
        } catch (ResponseStatusException ex) {
            log.warn("Linked DigestRequest id {} not found for DigestContent id {}", content.getRequestId(), content.getId());
        }
    }

    //
    // endregion DigestContent
    //

}
```
