```java
package com.java_template.prototype;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
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
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    /*
     * Caches for each entity type
     */
    private final ConcurrentHashMap<String, List<Job>> jobCache = new ConcurrentHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<Pet>> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<AdoptionRequest>> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    /*
     * --------------- JOB CRUD ---------------
     */

    @PostMapping("/jobs")
    public Map<String, Object> createJob(@RequestBody Job job) {
        logger.info("Received request to create Job: {}", job);
        try {
            if (!job.isValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Job data");
            }
            String id = addJob(job);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("status", "Job created and processed");
            logger.info("Job created with id {}", id);
            return resp;
        } catch (ResponseStatusException e) {
            logger.error("Error creating Job: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating Job", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @GetMapping("/jobs/{id}")
    public Job getJob(@PathVariable String id) {
        logger.info("Fetching Job with id: {}", id);
        return getEntityById(jobCache, id, Job.class, "Job");
    }

    @PutMapping("/jobs/{id}")
    public Map<String, Object> updateJob(@PathVariable String id, @RequestBody Job job) {
        logger.info("Updating Job with id: {}", id);
        try {
            if (!job.isValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Job data");
            }
            updateEntity(jobCache, id, job, Job.class, "Job");
            processJob(job);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("status", "Job updated and processed");
            logger.info("Job updated with id {}", id);
            return resp;
        } catch (ResponseStatusException e) {
            logger.error("Error updating Job: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error updating Job", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @DeleteMapping("/jobs/{id}")
    public Map<String, Object> deleteJob(@PathVariable String id) {
        logger.info("Deleting Job with id: {}", id);
        boolean removed = deleteEntity(jobCache, id, Job.class, "Job");
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Job deleted");
        logger.info("Deleted Job with id {}", id);
        return resp;
    }

    /*
     * --------------- PET CRUD ---------------
     */

    @PostMapping("/pets")
    public Map<String, Object> createPet(@RequestBody Pet pet) {
        logger.info("Received request to create Pet: {}", pet);
        try {
            if (!pet.isValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Pet data");
            }
            String id = addPet(pet);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("status", "Pet created and processed");
            logger.info("Pet created with id {}", id);
            return resp;
        } catch (ResponseStatusException e) {
            logger.error("Error creating Pet: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating Pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @GetMapping("/pets/{id}")
    public Pet getPet(@PathVariable String id) {
        logger.info("Fetching Pet with id: {}", id);
        return getEntityById(petCache, id, Pet.class, "Pet");
    }

    @PutMapping("/pets/{id}")
    public Map<String, Object> updatePet(@PathVariable String id, @RequestBody Pet pet) {
        logger.info("Updating Pet with id: {}", id);
        try {
            if (!pet.isValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Pet data");
            }
            updateEntity(petCache, id, pet, Pet.class, "Pet");
            processPet(pet);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("status", "Pet updated and processed");
            logger.info("Pet updated with id {}", id);
            return resp;
        } catch (ResponseStatusException e) {
            logger.error("Error updating Pet: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error updating Pet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @DeleteMapping("/pets/{id}")
    public Map<String, Object> deletePet(@PathVariable String id) {
        logger.info("Deleting Pet with id: {}", id);
        boolean removed = deleteEntity(petCache, id, Pet.class, "Pet");
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Pet deleted");
        logger.info("Deleted Pet with id {}", id);
        return resp;
    }

    /*
     * --------------- ADOPTIONREQUEST CRUD ---------------
     */

    @PostMapping("/adoptionRequests")
    public Map<String, Object> createAdoptionRequest(@RequestBody AdoptionRequest request) {
        logger.info("Received request to create AdoptionRequest: {}", request);
        try {
            if (!request.isValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid AdoptionRequest data");
            }
            String id = addAdoptionRequest(request);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("status", "AdoptionRequest created and processed");
            logger.info("AdoptionRequest created with id {}", id);
            return resp;
        } catch (ResponseStatusException e) {
            logger.error("Error creating AdoptionRequest: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating AdoptionRequest", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @GetMapping("/adoptionRequests/{id}")
    public AdoptionRequest getAdoptionRequest(@PathVariable String id) {
        logger.info("Fetching AdoptionRequest with id: {}", id);
        return getEntityById(adoptionRequestCache, id, AdoptionRequest.class, "AdoptionRequest");
    }

    @PutMapping("/adoptionRequests/{id}")
    public Map<String, Object> updateAdoptionRequest(@PathVariable String id, @RequestBody AdoptionRequest request) {
        logger.info("Updating AdoptionRequest with id: {}", id);
        try {
            if (!request.isValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid AdoptionRequest data");
            }
            updateEntity(adoptionRequestCache, id, request, AdoptionRequest.class, "AdoptionRequest");
            processAdoptionRequest(request);
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", id);
            resp.put("status", "AdoptionRequest updated and processed");
            logger.info("AdoptionRequest updated with id {}", id);
            return resp;
        } catch (ResponseStatusException e) {
            logger.error("Error updating AdoptionRequest: {}", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error updating AdoptionRequest", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        }
    }

    @DeleteMapping("/adoptionRequests/{id}")
    public Map<String, Object> deleteAdoptionRequest(@PathVariable String id) {
        logger.info("Deleting AdoptionRequest with id: {}", id);
        boolean removed = deleteEntity(adoptionRequestCache, id, AdoptionRequest.class, "AdoptionRequest");
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AdoptionRequest not found");
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "AdoptionRequest deleted");
        logger.info("Deleted AdoptionRequest with id {}", id);
        return resp;
    }

    /*
     * ====== COMMON CACHE OPERATIONS ======
     */

    private <T> T getEntityById(ConcurrentHashMap<String, List<T>> cache, String id, Class<T> clazz, String entityName) {
        List<T> entities = cache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, entityName + " not found");
        }
        Optional<T> found = entities.stream()
                .filter(e -> {
                    // Use reflection to get id property
                    try {
                        String eid = (String) clazz.getMethod("getId").invoke(e);
                        return eid.equals(id);
                    } catch (Exception ex) {
                        logger.error("Reflection error getting id for {}: {}", entityName, ex.getMessage());
                        return false;
                    }
                }).findFirst();
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, entityName + " not found");
        }
        return found.get();
    }

    private <T> void updateEntity(ConcurrentHashMap<String, List<T>> cache, String id, T newEntity, Class<T> clazz, String entityName) {
        List<T> entities = cache.get("entities");
        if (entities == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, entityName + " not found");
        }
        boolean updated = false;
        for (int i = 0; i < entities.size(); i++) {
            T e = entities.get(i);
            try {
                String eid = (String) clazz.getMethod("getId").invoke(e);
                if (eid.equals(id)) {
                    // Preserve technicalId from old entity if newEntity technicalId is null
                    Object oldTechId = clazz.getMethod("getTechnicalId").invoke(e);
                    Object newTechId = clazz.getMethod("getTechnicalId").invoke(newEntity);
                    if (newTechId == null && oldTechId != null) {
                        clazz.getMethod("setTechnicalId", UUID.class).invoke(newEntity, oldTechId);
                    }
                    // Make sure id is consistent
                    clazz.getMethod("setId", String.class).invoke(newEntity, id);
                    entities.set(i, newEntity);
                    updated = true;
                    break;
                }
            } catch (Exception ex) {
                logger.error("Reflection error updating {}: {}", entityName, ex.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Reflection error");
            }
        }
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, entityName + " not found");
        }
    }

    private <T> boolean deleteEntity(ConcurrentHashMap<String, List<T>> cache, String id, Class<T> clazz, String entityName) {
        List<T> entities = cache.get("entities");
        if (entities == null) return false;
        Iterator<T> it = entities.iterator();
        while (it.hasNext()) {
            T e = it.next();
            try {
                String eid = (String) clazz.getMethod("getId").invoke(e);
                if (eid.equals(id)) {
                    it.remove();
                    return true;
                }
            } catch (Exception ex) {
                logger.error("Reflection error deleting {}: {}", entityName, ex.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Reflection error");
            }
        }
        return false;
    }

    /*
     * ====== ADD ENTITY METHODS ======
     */

    private String addJob(Job job) {
        String id = String.valueOf(jobIdCounter.getAndIncrement());
        job.setId(id);
        if (job.getTechnicalId() == null) {
            job.setTechnicalId(UUID.randomUUID());
        }
        jobCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(job);
        processJob(job);
        return id;
    }

    private String addPet(Pet pet) {
        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        if (pet.getTechnicalId() == null) {
            pet.setTechnicalId(UUID.randomUUID());
        }
        petCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(pet);
        processPet(pet);
        return id;
    }

    private String addAdoptionRequest(AdoptionRequest request) {
        String id = String.valueOf(adoptionRequestIdCounter.getAndIncrement());
        request.setId(id);
        if (request.getTechnicalId() == null) {
            request.setTechnicalId(UUID.randomUUID());
        }
        adoptionRequestCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(request);
        processAdoptionRequest(request);
        return id;
    }

    /*
     * ====== PROCESS ENTITY METHODS (Event simulation) ======
     */

    private void processJob(Job job) {
        // TODO: Replace with real event processing logic.
        logger.info("Processing Job event for job id: {}, name: {}", job.getId(), job.getName());
        // Example: simulate some processing delay or update status
    }

    private void processPet(Pet pet) {
        // TODO: Replace with real event processing logic.
        logger.info("Processing Pet event for pet id: {}, name: {}", pet.getId(), pet.getName());
        // Example: simulate validation or enrichment
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        // TODO: Replace with real event processing logic.
        logger.info("Processing AdoptionRequest event for id: {}, petId: {}, adopterId: {}",
                request.getId(), request.getPetId(), request.getAdopterId());
        // Example: simulate approval workflow or notification
    }

}
```
