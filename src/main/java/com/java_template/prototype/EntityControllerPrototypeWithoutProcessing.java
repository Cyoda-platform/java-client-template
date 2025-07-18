package com.java_template.prototype;

import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Task;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

@Validated
@RestController
@RequestMapping(path = "/prototype")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final ConcurrentHashMap<String, List<Job>> jobCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Task>> taskCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Pet>> petCache = new ConcurrentHashMap<>();

    private final AtomicLong jobIdCounter = new AtomicLong(1);
    private final AtomicLong taskIdCounter = new AtomicLong(1);
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // DTOs for POST/PUT requests with validation (flat fields, no nested objects)
    @Data
    public static class JobDto {
        @NotBlank
        private String type;

        @NotBlank
        private String status;

        private String parameters;
    }

    @Data
    public static class TaskDto {
        @NotBlank
        private String jobId;

        @NotBlank
        private String type;

        @NotBlank
        private String status;

        private String result;
    }

    @Data
    public static class PetDto {
        @NotBlank
        private String name;

        @NotBlank
        private String type;

        @NotBlank
        private String status;

        @NotNull
        private Integer age;
    }

    // --- JOB CRUD ---

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobDto jobDto) {
        logger.info("Received request to create Job with DTO: {}", jobDto);
        Job job = new Job();
        job.setId(null); // will be set by addJob
        job.setTechnicalId(UUID.randomUUID());
        job.setType(jobDto.getType());
        job.setStatus(jobDto.getStatus());
        job.setParameters(jobDto.getParameters());
        if (!job.isValid()) {
            logger.error("Invalid Job data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job data");
        }
        String id = addJob(job);
        logger.info("Job created with id {}", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Job processed");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable @NotBlank String id) {
        logger.info("Fetching Job with id {}", id);
        Job job = getJobById(id);
        return ResponseEntity.ok(job);
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<Job> updateJob(@PathVariable @NotBlank String id, @RequestBody @Valid JobDto jobDto) {
        logger.info("Updating Job with id {} and DTO: {}", id, jobDto);
        Job job = new Job();
        job.setId(id);
        job.setTechnicalId(UUID.randomUUID());
        job.setType(jobDto.getType());
        job.setStatus(jobDto.getStatus());
        job.setParameters(jobDto.getParameters());
        if (!job.isValid()) {
            logger.error("Invalid Job data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job data");
        }
        Job updated = updateJobInCache(id, job);
        logger.info("Job updated: {}", updated);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable @NotBlank String id) {
        logger.info("Deleting Job with id {}", id);
        boolean removed = deleteJobFromCache(id);
        if (!removed) {
            logger.error("Job with id {} not found for deletion", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found");
        }
        logger.info("Job deleted with id {}", id);
        return ResponseEntity.ok(Collections.singletonMap("status", "Job deleted"));
    }

    // --- TASK CRUD ---

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody @Valid TaskDto taskDto) {
        logger.info("Received request to create Task with DTO: {}", taskDto);
        Task task = new Task();
        task.setId(null);
        task.setTechnicalId(UUID.randomUUID());
        task.setJobId(taskDto.getJobId());
        task.setType(taskDto.getType());
        task.setStatus(taskDto.getStatus());
        task.setResult(taskDto.getResult());
        if (!task.isValid()) {
            logger.error("Invalid Task data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Task data");
        }
        String id = addTask(task);
        logger.info("Task created with id {}", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Task processed");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable @NotBlank String id) {
        logger.info("Fetching Task with id {}", id);
        Task task = getTaskById(id);
        return ResponseEntity.ok(task);
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable @NotBlank String id, @RequestBody @Valid TaskDto taskDto) {
        logger.info("Updating Task with id {} and DTO: {}", id, taskDto);
        Task task = new Task();
        task.setId(id);
        task.setTechnicalId(UUID.randomUUID());
        task.setJobId(taskDto.getJobId());
        task.setType(taskDto.getType());
        task.setStatus(taskDto.getStatus());
        task.setResult(taskDto.getResult());
        if (!task.isValid()) {
            logger.error("Invalid Task data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Task data");
        }
        Task updated = updateTaskInCache(id, task);
        logger.info("Task updated: {}", updated);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, String>> deleteTask(@PathVariable @NotBlank String id) {
        logger.info("Deleting Task with id {}", id);
        boolean removed = deleteTaskFromCache(id);
        if (!removed) {
            logger.error("Task with id {} not found for deletion", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Task not found");
        }
        logger.info("Task deleted with id {}", id);
        return ResponseEntity.ok(Collections.singletonMap("status", "Task deleted"));
    }

    // --- PET CRUD ---

    @PostMapping("/pets")
    public ResponseEntity<Map<String, Object>> createPet(@RequestBody @Valid PetDto petDto) {
        logger.info("Received request to create Pet with DTO: {}", petDto);
        Pet pet = new Pet();
        pet.setId(null);
        pet.setTechnicalId(UUID.randomUUID());
        pet.setName(petDto.getName());
        pet.setType(petDto.getType());
        pet.setStatus(petDto.getStatus());
        pet.setAge(petDto.getAge());
        if (!pet.isValid()) {
            logger.error("Invalid Pet data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Pet data");
        }
        String id = addPet(pet);
        logger.info("Pet created with id {}", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Pet processed");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable @NotBlank String id) {
        logger.info("Fetching Pet with id {}", id);
        Pet pet = getPetById(id);
        return ResponseEntity.ok(pet);
    }

    @PutMapping("/pets/{id}")
    public ResponseEntity<Pet> updatePet(@PathVariable @NotBlank String id, @RequestBody @Valid PetDto petDto) {
        logger.info("Updating Pet with id {} and DTO: {}", id, petDto);
        Pet pet = new Pet();
        pet.setId(id);
        pet.setTechnicalId(UUID.randomUUID());
        pet.setName(petDto.getName());
        pet.setType(petDto.getType());
        pet.setStatus(petDto.getStatus());
        pet.setAge(petDto.getAge());
        if (!pet.isValid()) {
            logger.error("Invalid Pet data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Pet data");
        }
        Pet updated = updatePetInCache(id, pet);
        logger.info("Pet updated: {}", updated);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/pets/{id}")
    public ResponseEntity<Map<String, String>> deletePet(@PathVariable @NotBlank String id) {
        logger.info("Deleting Pet with id {}", id);
        boolean removed = deletePetFromCache(id);
        if (!removed) {
            logger.error("Pet with id {} not found for deletion", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        logger.info("Pet deleted with id {}", id);
        return ResponseEntity.ok(Collections.singletonMap("status", "Pet deleted"));
    }

    // ======= JOB Cache Methods =======

    private String addJob(Job job) {
        String id = String.valueOf(jobIdCounter.getAndIncrement());
        job.setId(id);
        jobCache.computeIfAbsent("jobs", k -> Collections.synchronizedList(new ArrayList<>())).add(job);
        logger.info("Job added to cache: {}", job);
        return id;
    }

    private Job getJobById(String id) {
        List<Job> jobs = jobCache.getOrDefault("jobs", Collections.emptyList());
        return jobs.stream()
                .filter(j -> id.equals(j.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found with id " + id));
    }

    private Job updateJobInCache(String id, Job job) {
        List<Job> jobs = jobCache.getOrDefault("jobs", Collections.emptyList());
        synchronized (jobs) {
            for (int i = 0; i < jobs.size(); i++) {
                if (id.equals(jobs.get(i).getId())) {
                    jobs.set(i, job);
                    logger.info("Job updated in cache: {}", job);
                    return job;
                }
            }
        }
        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found with id " + id);
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
        return id;
    }

    private Task getTaskById(String id) {
        List<Task> tasks = taskCache.getOrDefault("tasks", Collections.emptyList());
        return tasks.stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Task not found with id " + id));
    }

    private Task updateTaskInCache(String id, Task task) {
        List<Task> tasks = taskCache.getOrDefault("tasks", Collections.emptyList());
        synchronized (tasks) {
            for (int i = 0; i < tasks.size(); i++) {
                if (id.equals(tasks.get(i).getId())) {
                    tasks.set(i, task);
                    logger.info("Task updated in cache: {}", task);
                    return task;
                }
            }
        }
        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Task not found with id " + id);
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
        return id;
    }

    private Pet getPetById(String id) {
        List<Pet> pets = petCache.getOrDefault("pets", Collections.emptyList());
        return pets.stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + id));
    }

    private Pet updatePetInCache(String id, Pet pet) {
        List<Pet> pets = petCache.getOrDefault("pets", Collections.emptyList());
        synchronized (pets) {
            for (int i = 0; i < pets.size(); i++) {
                if (id.equals(pets.get(i).getId())) {
                    pets.set(i, pet);
                    logger.info("Pet updated in cache: {}", pet);
                    return pet;
                }
            }
        }
        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + id);
    }

    private boolean deletePetFromCache(String id) {
        List<Pet> pets = petCache.getOrDefault("pets", Collections.emptyList());
        synchronized (pets) {
            return pets.removeIf(p -> id.equals(p.getId()));
        }
    }

}
