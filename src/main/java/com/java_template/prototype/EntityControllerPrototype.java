```java
package com.java_template.prototype;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatch;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping(path = "/prototype/entity")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // Caches for entities; key "entities" holds all entities of that type
    private final ConcurrentHashMap<String, List<DigestRequest>> digestRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<DigestData>> digestDataCache = new ConcurrentHashMap<>();
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<EmailDispatch>> emailDispatchCache = new ConcurrentHashMap<>();
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    // ====== DIGEST REQUEST CRUD ======

    @PostMapping("/digestRequest")
    public Map<String, Object> createDigestRequest(@RequestBody DigestRequest digestRequest) {
        logger.info("Received request to create DigestRequest: {}", digestRequest);

        // Validate entity before saving
        if (!digestRequest.isValid()) {
            logger.error("Invalid DigestRequest data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest data");
        }

        String id = addDigestRequest(digestRequest);
        logger.info("DigestRequest created with id {}", id);

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "created");
        return response;
    }

    @GetMapping("/digestRequest/{id}")
    public DigestRequest getDigestRequest(@PathVariable String id) {
        logger.info("Fetching DigestRequest with id {}", id);
        DigestRequest dr = getDigestRequestById(id);
        if (dr == null) {
            logger.error("DigestRequest with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        return dr;
    }

    @PutMapping("/digestRequest/{id}")
    public Map<String, Object> updateDigestRequest(@PathVariable String id, @RequestBody DigestRequest digestRequest) {
        logger.info("Updating DigestRequest with id {}", id);

        if (!digestRequest.isValid()) {
            logger.error("Invalid DigestRequest data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest data");
        }

        boolean updated = updateDigestRequestById(id, digestRequest);
        if (!updated) {
            logger.error("DigestRequest with id {} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "updated");
        return response;
    }

    @DeleteMapping("/digestRequest/{id}")
    public Map<String, Object> deleteDigestRequest(@PathVariable String id) {
        logger.info("Deleting DigestRequest with id {}", id);
        boolean deleted = deleteDigestRequestById(id);
        if (!deleted) {
            logger.error("DigestRequest with id {} not found for delete", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "deleted");
        return response;
    }

    // ====== DIGEST DATA CRUD ======

    @PostMapping("/digestData")
    public Map<String, Object> createDigestData(@RequestBody DigestData digestData) {
        logger.info("Received request to create DigestData: {}", digestData);

        if (!digestData.isValid()) {
            logger.error("Invalid DigestData data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestData data");
        }

        String id = addDigestData(digestData);
        logger.info("DigestData created with id {}", id);

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "created");
        return response;
    }

    @GetMapping("/digestData/{id}")
    public DigestData getDigestData(@PathVariable String id) {
        logger.info("Fetching DigestData with id {}", id);
        DigestData dd = getDigestDataById(id);
        if (dd == null) {
            logger.error("DigestData with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found");
        }
        return dd;
    }

    @PutMapping("/digestData/{id}")
    public Map<String, Object> updateDigestData(@PathVariable String id, @RequestBody DigestData digestData) {
        logger.info("Updating DigestData with id {}", id);

        if (!digestData.isValid()) {
            logger.error("Invalid DigestData data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestData data");
        }

        boolean updated = updateDigestDataById(id, digestData);
        if (!updated) {
            logger.error("DigestData with id {} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "updated");
        return response;
    }

    @DeleteMapping("/digestData/{id}")
    public Map<String, Object> deleteDigestData(@PathVariable String id) {
        logger.info("Deleting DigestData with id {}", id);
        boolean deleted = deleteDigestDataById(id);
        if (!deleted) {
            logger.error("DigestData with id {} not found for delete", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "deleted");
        return response;
    }

    // ====== EMAIL DISPATCH CRUD ======

    @PostMapping("/emailDispatch")
    public Map<String, Object> createEmailDispatch(@RequestBody EmailDispatch emailDispatch) {
        logger.info("Received request to create EmailDispatch: {}", emailDispatch);

        if (!emailDispatch.isValid()) {
            logger.error("Invalid EmailDispatch data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid EmailDispatch data");
        }

        String id = addEmailDispatch(emailDispatch);
        logger.info("EmailDispatch created with id {}", id);

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "created");
        return response;
    }

    @GetMapping("/emailDispatch/{id}")
    public EmailDispatch getEmailDispatch(@PathVariable String id) {
        logger.info("Fetching EmailDispatch with id {}", id);
        EmailDispatch ed = getEmailDispatchById(id);
        if (ed == null) {
            logger.error("EmailDispatch with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found");
        }
        return ed;
    }

    @PutMapping("/emailDispatch/{id}")
    public Map<String, Object> updateEmailDispatch(@PathVariable String id, @RequestBody EmailDispatch emailDispatch) {
        logger.info("Updating EmailDispatch with id {}", id);

        if (!emailDispatch.isValid()) {
            logger.error("Invalid EmailDispatch data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid EmailDispatch data");
        }

        boolean updated = updateEmailDispatchById(id, emailDispatch);
        if (!updated) {
            logger.error("EmailDispatch with id {} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "updated");
        return response;
    }

    @DeleteMapping("/emailDispatch/{id}")
    public Map<String, Object> deleteEmailDispatch(@PathVariable String id) {
        logger.info("Deleting EmailDispatch with id {}", id);
        boolean deleted = deleteEmailDispatchById(id);
        if (!deleted) {
            logger.error("EmailDispatch with id {} not found for delete", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EmailDispatch not found");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("status", "deleted");
        return response;
    }

    // ======= Cache helpers and event simulation =======

    private String addDigestRequest(DigestRequest entity) {
        String id = String.valueOf(digestRequestIdCounter.getAndIncrement());
        entity.setId(id);
        entity.setTechnicalId(UUID.randomUUID());

        digestRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);

        processDigestRequest(entity);
        return id;
    }

    private DigestRequest getDigestRequestById(String id) {
        List<DigestRequest> list = digestRequestCache.get("entities");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
        }
    }

    private boolean updateDigestRequestById(String id, DigestRequest updated) {
        List<DigestRequest> list = digestRequestCache.get("entities");
        if (list == null) return false;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                DigestRequest current = list.get(i);
                if (id.equals(current.getId())) {
                    updated.setId(id);
                    updated.setTechnicalId(current.getTechnicalId());
                    list.set(i, updated);
                    processDigestRequest(updated);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean deleteDigestRequestById(String id) {
        List<DigestRequest> list = digestRequestCache.get("entities");
        if (list == null) return false;
        synchronized (list) {
            return list.removeIf(e -> id.equals(e.getId()));
        }
    }

    private void processDigestRequest(DigestRequest entity) {
        logger.info("Simulating processing of DigestRequest id={} for userEmail={}", entity.getId(), entity.getUserEmail());
        // TODO: Add real event processing logic here or call Cyoda workflow trigger
    }

    private String addDigestData(DigestData entity) {
        String id = String.valueOf(digestDataIdCounter.getAndIncrement());
        entity.setId(id);
        entity.setTechnicalId(UUID.randomUUID());

        digestDataCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);

        processDigestData(entity);
        return id;
    }

    private DigestData getDigestDataById(String id) {
        List<DigestData> list = digestDataCache.get("entities");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
        }
    }

    private boolean updateDigestDataById(String id, DigestData updated) {
        List<DigestData> list = digestDataCache.get("entities");
        if (list == null) return false;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                DigestData current = list.get(i);
                if (id.equals(current.getId())) {
                    updated.setId(id);
                    updated.setTechnicalId(current.getTechnicalId());
                    list.set(i, updated);
                    processDigestData(updated);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean deleteDigestDataById(String id) {
        List<DigestData> list = digestDataCache.get("entities");
        if (list == null) return false;
        synchronized (list) {
            return list.removeIf(e -> id.equals(e.getId()));
        }
    }

    private void processDigestData(DigestData entity) {
        logger.info("Simulating processing of DigestData id={} with dataPayload={}", entity.getId(), entity.getDataPayload());
        // TODO: Add real event processing logic here or call Cyoda workflow trigger
    }

    private String addEmailDispatch(EmailDispatch entity) {
        String id = String.valueOf(emailDispatchIdCounter.getAndIncrement());
        entity.setId(id);
        entity.setTechnicalId(UUID.randomUUID());

        emailDispatchCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(entity);

        processEmailDispatch(entity);
        return id;
    }

    private EmailDispatch getEmailDispatchById(String id) {
        List<EmailDispatch> list = emailDispatchCache.get("entities");
        if (list == null) return null;
        synchronized (list) {
            return list.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
        }
    }

    private boolean updateEmailDispatchById(String id, EmailDispatch updated) {
        List<EmailDispatch> list = emailDispatchCache.get("entities");
        if (list == null) return false;
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                EmailDispatch current = list.get(i);
                if (id.equals(current.getId())) {
                    updated.setId(id);
                    updated.setTechnicalId(current.getTechnicalId());
                    list.set(i, updated);
                    processEmailDispatch(updated);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean deleteEmailDispatchById(String id) {
        List<EmailDispatch> list = emailDispatchCache.get("entities");
        if (list == null) return false;
        synchronized (list) {
            return list.removeIf(e -> id.equals(e.getId()));
        }
    }

    private void processEmailDispatch(EmailDispatch entity) {
        logger.info("Simulating processing of EmailDispatch id={} with status={}", entity.getId(), entity.getStatus());
        // TODO: Add real event processing logic here or call Cyoda workflow trigger
    }
}
```
