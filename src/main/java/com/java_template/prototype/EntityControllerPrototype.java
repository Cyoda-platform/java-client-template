```java
package com.java_template.prototype;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Task;
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

    // Local caches for entities
    private final ConcurrentHashMap<String, List<Job>> jobCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Task>> taskCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Pet>> petCache = new ConcurrentHashMap<>();

    // Atomic counters for IDs
    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong taskIdCounter = new AtomicLong(1);
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // --- JOB CRUD ---

    @PostMapping("/jobs")
    public Map<String, Object> createJob(@RequestBody Job job) {
        logger.info("Received request to create Job: {}", job);
        if (!job.isValid()) {
            logger.error("Invalid Job data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Job data");
        }
        String id = addJob(job);
        logger.info("Job created with id {}", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Job processed");
        return resp;
    }

    @GetMapping("/jobs/{id}")
    public Job getJob(@PathVariable String id) {
        logger.info("Fetching Job with id {}", id);
        return getJobById(id);
    }

    @PutMapping("/jobs/{id}")
    public Job updateJob(@PathVariable String id, @RequestBody Job job) {
        logger.info("Updating Job with id {}: {}", id, job);
        if (!job.isValid()) {
            logger.error("Invalid Job data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Job data");
        }
        job.setId(id);
        Job updated = updateJobInCache(id, job);
        logger.info("Job updated: {}", updated);
        return updated;
    }

    @DeleteMapping("/jobs/{id}")
    public Map<String, String> deleteJob(@PathVariable String id) {
        logger.info("Deleting Job with id {}", id);
        boolean removed = deleteJobFromCache(id);
        if (!removed) {
            logger.error("Job with id {} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        logger.info("Job deleted with id {}", id);
        return Collections.singletonMap("status", "Job deleted");
    }

    // --- TASK CRUD ---

    @PostMapping("/tasks")
    public Map<String, Object> createTask(@RequestBody Task task) {
        logger.info("Received request to create Task: {}", task);
        if (!task.isValid()) {
            logger.error("Invalid Task data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Task data");
        }
        String id = addTask(task);
        logger.info("Task created with id {}", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Task processed");
        return resp;
    }

    @GetMapping("/tasks/{id}")
    public Task getTask(@PathVariable String id) {
        logger.info("Fetching Task with id {}", id);
        return getTaskById(id);
    }

    @PutMapping("/tasks/{id}")
    public Task updateTask(@PathVariable String id, @RequestBody Task task) {
        logger.info("Updating Task with id {}: {}", id, task);
        if (!task.isValid()) {
            logger.error("Invalid Task data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Task data");
        }
        task.setId(id);
        Task updated = updateTaskInCache(id, task);
        logger.info("Task updated: {}", updated);
        return updated;
    }

    @DeleteMapping("/tasks/{id}")
    public Map<String, String> deleteTask(@PathVariable String id) {
        logger.info("Deleting Task with id {}", id);
        boolean removed = deleteTaskFromCache(id);
        if (!removed) {
            logger.error("Task with id {} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        logger.info("Task deleted with id {}", id);
        return Collections.singletonMap("status", "Task deleted");
    }

    // --- PET CRUD ---

    @PostMapping("/pets")
    public Map<String, Object> createPet(@RequestBody Pet pet) {
        logger.info("Received request to create Pet: {}", pet);
        if (!pet.isValid()) {
            logger.error("Invalid Pet data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Pet data");
        }
        String id = addPet(pet);
        logger.info("Pet created with id {}", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Pet processed");
        return resp;
    }

    @GetMapping("/pets/{id}")
    public Pet getPet(@PathVariable String id) {
        logger.info("Fetching Pet with id {}", id);
        return getPetById(id);
    }

    @PutMapping("/pets/{id}")
    public Pet updatePet(@PathVariable String id, @RequestBody Pet pet) {
        logger.info("Updating Pet with id {}: {}", id, pet);
        if (!pet.isValid()) {
            logger.error("Invalid Pet data");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Pet data");
        }
        pet.setId(id);
        Pet updated = updatePetInCache(id, pet);
        logger.info("Pet updated: {}", updated);
        return updated;
    }

    @DeleteMapping("/pets/{id}")
    public Map<String, String> deletePet(@PathVariable String id) {
        logger.info("Deleting Pet with id {}", id);
        boolean removed = deletePetFromCache(id);
        if (!removed) {
            logger.error("Pet with id {} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        logger.info("Pet deleted with id {}", id);
        return Collections.singletonMap("status", "Pet deleted");
    }

    // ======= JOB Cache Methods =======

    private String addJob(Job job) {
        String id = String.valueOf(jobIdCounter.getAndIncrement());
        job.setId(id);
        jobCache.computeIfAbsent("jobs", k -> Collections.synchronizedList(new ArrayList<>())).add(job);
        logger.info("Job added to cache: {}", job);
        processJob(job);
        return id;
    }

    private Job getJobById(String id) {
        List<Job> jobs = jobCache.getOrDefault("jobs", Collections.emptyList());
        return jobs.stream()
                .filter(j -> id.equals(j.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found with id " + id));
    }

    private Job updateJobInCache(String id, Job job) {
        List<Job> jobs = jobCache.getOrDefault("jobs", Collections.emptyList());
        synchronized (jobs) {
            for (int i = 0; i < jobs.size(); i++) {
                if (id.equals(jobs.get(i).getId())) {
                    jobs.set(i, job);
                    logger.info("Job updated in cache: {}", job);
                    processJob(job);
                    return job;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found with id " + id);
    }

    private boolean deleteJobFromCache(String id) {
        List<Job> jobs = jobCache.getOrDefault("jobs", Collections.emptyList());
        synchronized (jobs) {
            return jobs.removeIf(j -> id.equals(j.getId()));
        }
    }

    // ======= TASK Cache Methods =======

    private String addTask(Task task) {
        String id = String.valueOf(taskIdCounter.getAndIncrement());
        task.setId(id);
        taskCache.computeIfAbsent("tasks", k -> Collections.synchronizedList(new ArrayList<>())).add(task);
        logger.info("Task added to cache: {}", task);
        processTask(task);
        return id;
    }

    private Task getTaskById(String id) {
        List<Task> tasks = taskCache.getOrDefault("tasks", Collections.emptyList());
        return tasks.stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found with id " + id));
    }

    private Task updateTaskInCache(String id, Task task) {
        List<Task> tasks = taskCache.getOrDefault("tasks", Collections.emptyList());
        synchronized (tasks) {
            for (int i = 0; i < tasks.size(); i++) {
                if (id.equals(tasks.get(i).getId())) {
                    tasks.set(i, task);
                    logger.info("Task updated in cache: {}", task);
                    processTask(task);
                    return task;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found with id " + id);
    }

    private boolean deleteTaskFromCache(String id) {
        List<Task> tasks = taskCache.getOrDefault("tasks", Collections.emptyList());
        synchronized (tasks) {
            return tasks.removeIf(t -> id.equals(t.getId()));
        }
    }

    // ======= PET Cache Methods =======

    private String addPet(Pet pet) {
        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        petCache.computeIfAbsent("pets", k -> Collections.synchronizedList(new ArrayList<>())).add(pet);
        logger.info("Pet added to cache: {}", pet);
        processPet(pet);
        return id;
    }

    private Pet getPetById(String id) {
        List<Pet> pets = petCache.getOrDefault("pets", Collections.emptyList());
        return pets.stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + id));
    }

    private Pet updatePetInCache(String id, Pet pet) {
        List<Pet> pets = petCache.getOrDefault("pets", Collections.emptyList());
        synchronized (pets) {
            for (int i = 0; i < pets.size(); i++) {
                if (id.equals(pets.get(i).getId())) {
                    pets.set(i, pet);
                    logger.info("Pet updated in cache: {}", pet);
                    processPet(pet);
                    return pet;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + id);
    }

    private boolean deletePetFromCache(String id) {
        List<Pet> pets = petCache.getOrDefault("pets", Collections.emptyList());
        synchronized (pets) {
            return pets.removeIf(p -> id.equals(p.getId()));
        }
    }

    // ======= Event Processing Stubs =======

    private void processJob(Job job) {
        // TODO: Replace with actual Cyoda event processing logic
        logger.info("Processing Job event for job id: {}", job.getId());
        // Example dummy logic: just log
    }

    private void processTask(Task task) {
        // TODO: Replace with actual Cyoda event processing logic
        logger.info("Processing Task event for task id: {}", task.getId());
        // Example dummy logic: just log
    }

    private void processPet(Pet pet) {
        // TODO: Replace with actual Cyoda event processing logic
        logger.info("Processing Pet event for pet id: {}", pet.getId());
        // Example dummy logic: just log
    }
}
```
