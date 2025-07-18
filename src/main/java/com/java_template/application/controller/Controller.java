package com.java_template.prototype;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Task;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/prototype-main")
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper = entityService.getObjectMapper();

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

    // PET is minor entity, keep local cache for Pet
    private final Map<String, Pet> petCache = Collections.synchronizedMap(new HashMap<>());
    private long petIdCounter = 1;

    // --- JOB CRUD ---

    @PostMapping("/jobs")
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobDto jobDto) throws ExecutionException, InterruptedException {
        logger.info("Received request to create Job with DTO: {}", jobDto);
        Job job = new Job();
        job.setTechnicalId(null);
        job.setType(jobDto.getType());
        job.setStatus(jobDto.getStatus());
        job.setParameters(jobDto.getParameters());
        if (!job.isValid()) {
            logger.error("Invalid Job data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job data");
        }
        CompletableFuture<UUID> idFuture = entityService.addItem("job", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        String id = technicalId.toString();
        logger.info("Job created with technicalId {}", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Job processed");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getJob(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Fetching Job with id {}", id);
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("job", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Job not found with id " + id);
        }
        Job job = objectMapper.treeToValue(node, Job.class);
        job.setId(id);
        job.setTechnicalId(technicalId);
        return ResponseEntity.ok(job);
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<Job> updateJob(@PathVariable @NotBlank String id, @RequestBody @Valid JobDto jobDto) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Updating Job with id {} and DTO: {}", id, jobDto);
        UUID technicalId = UUID.fromString(id);
        Job job = new Job();
        job.setId(id);
        job.setTechnicalId(technicalId);
        job.setType(jobDto.getType());
        job.setStatus(jobDto.getStatus());
        job.setParameters(jobDto.getParameters());
        if (!job.isValid()) {
            logger.error("Invalid Job data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Job data");
        }
        CompletableFuture<UUID> updatedItemId = entityService.updateItem("job", ENTITY_VERSION, technicalId, job);
        updatedItemId.get();
        logger.info("Job updated: {}", job);
        return ResponseEntity.ok(job);
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Map<String, String>> deleteJob(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Deleting Job with id {}", id);
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem("job", ENTITY_VERSION, technicalId);
        deletedItemId.get();
        logger.info("Job deleted with id {}", id);
        return ResponseEntity.ok(Collections.singletonMap("status", "Job deleted"));
    }

    // --- TASK CRUD ---

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody @Valid TaskDto taskDto) throws ExecutionException, InterruptedException {
        logger.info("Received request to create Task with DTO: {}", taskDto);
        Task task = new Task();
        task.setTechnicalId(null);
        task.setJobId(taskDto.getJobId());
        task.setType(taskDto.getType());
        task.setStatus(taskDto.getStatus());
        task.setResult(taskDto.getResult());
        if (!task.isValid()) {
            logger.error("Invalid Task data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Task data");
        }
        CompletableFuture<UUID> idFuture = entityService.addItem("task", ENTITY_VERSION, task);
        UUID technicalId = idFuture.get();
        String id = technicalId.toString();
        logger.info("Task created with technicalId {}", id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("status", "Task processed");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Fetching Task with id {}", id);
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("task", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Task not found with id " + id);
        }
        Task task = objectMapper.treeToValue(node, Task.class);
        task.setId(id);
        task.setTechnicalId(technicalId);
        return ResponseEntity.ok(task);
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable @NotBlank String id, @RequestBody @Valid TaskDto taskDto) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Updating Task with id {} and DTO: {}", id, taskDto);
        UUID technicalId = UUID.fromString(id);
        Task task = new Task();
        task.setId(id);
        task.setTechnicalId(technicalId);
        task.setJobId(taskDto.getJobId());
        task.setType(taskDto.getType());
        task.setStatus(taskDto.getStatus());
        task.setResult(taskDto.getResult());
        if (!task.isValid()) {
            logger.error("Invalid Task data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid Task data");
        }
        CompletableFuture<UUID> updatedItemId = entityService.updateItem("task", ENTITY_VERSION, technicalId, task);
        updatedItemId.get();
        logger.info("Task updated: {}", task);
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Map<String, String>> deleteTask(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Deleting Task with id {}", id);
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem("task", ENTITY_VERSION, technicalId);
        deletedItemId.get();
        logger.info("Task deleted with id {}", id);
        return ResponseEntity.ok(Collections.singletonMap("status", "Task deleted"));
    }

    // --- PET CRUD - keep local cache ---

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

    @PostMapping("/pets")
    public ResponseEntity<Map<String, Object>> createPet(@RequestBody @Valid PetDto petDto) {
        logger.info("Received request to create Pet with DTO: {}", petDto);
        Pet pet = new Pet();
        pet.setId(null);
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

    // ======= PET Cache Methods =======

    private synchronized String addPet(Pet pet) {
        String id = String.valueOf(petIdCounter++);
        pet.setId(id);
        petCache.put(id, pet);
        logger.info("Pet added to local cache: {}", pet);
        return id;
    }

    private Pet getPetById(String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + id);
        }
        return pet;
    }

    private Pet updatePetInCache(String id, Pet pet) {
        if (!petCache.containsKey(id)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + id);
        }
        pet.setId(id);
        petCache.put(id, pet);
        logger.info("Pet updated in local cache: {}", pet);
        return pet;
    }

    private boolean deletePetFromCache(String id) {
        return petCache.remove(id) != null;
    }

}