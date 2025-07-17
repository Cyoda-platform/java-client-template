package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.prototype.CyodaEntityControllerPrototype.Appointment;
import com.java_template.prototype.CyodaEntityControllerPrototype.DigestRequest;
import com.java_template.prototype.CyodaEntityControllerPrototype.DigestRequestResponse;
import com.java_template.prototype.CyodaEntityControllerPrototype.ErrorResponse;
import com.java_template.prototype.CyodaEntityControllerPrototype.JobStatus;
import com.java_template.prototype.CyodaEntityControllerPrototype.JobStatusResponse;
import com.java_template.prototype.CyodaEntityControllerPrototype.Owner;
import com.java_template.prototype.CyodaEntityControllerPrototype.Pet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/prototype/digest")
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private final ObjectMapper objectMapper;

    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/request")
    public ResponseEntity<DigestRequestResponse> submitDigestRequest(@RequestBody @Valid DigestRequest request) {
        log.info("Received digest request for email={} digestType={} status={}", request.getEmail(), request.getDigestType(), request.getStatus());
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        entityJobs.put(jobId, new JobStatus("processing", requestedAt));
        CompletableFuture.runAsync(() -> processDigestRequest(jobId, request));
        return ResponseEntity.accepted().body(new DigestRequestResponse(jobId, "accepted"));
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<JobStatusResponse> getDigestStatus(@PathVariable("requestId") @NotBlank String requestId) {
        JobStatus status = entityJobs.get(requestId);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "RequestId not found");
        }
        JobStatusResponse response = new JobStatusResponse(
                requestId,
                status.getEmail(),
                status.getStatus(),
                status.getSentAt(),
                status.getDigestSummary()
        );
        return ResponseEntity.ok(response);
    }

    // Removed business logic from processDigestRequest, keeping only job status management and logging
    protected void processDigestRequest(String jobId, DigestRequest request) {
        log.info("Processing digest jobId={} for email={}", jobId, request.getEmail());
        // Business logic moved to processors/criteria - this method should only manage job status updates if needed
        // For this refactor, we keep it empty or minimal to avoid business logic duplication
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.toString());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unexpected exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error"));
    }

    @GetMapping("/pets/{id}")
    public CompletableFuture<ResponseEntity<Pet>> getPet(@PathVariable UUID id) {
        return entityService.getItem("Pet", ENTITY_VERSION, id)
                .thenApply(itemNode -> {
                    if (itemNode == null || itemNode.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                    }
                    try {
                        Pet pet = objectMapper.treeToValue(itemNode, Pet.class);
                        pet.setTechnicalId(UUID.fromString(itemNode.get("technicalId").asText()));
                        return ResponseEntity.ok(pet);
                    } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error parsing Pet data");
                    }
                });
    }

    @GetMapping("/pets")
    public CompletableFuture<List<Pet>> getAllPets() {
        return entityService.getItems("Pet", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    try {
                        List<Pet> pets = objectMapper.readerForListOf(Pet.class).readValue(arrayNode);
                        for (int i = 0; i < arrayNode.size(); i++) {
                            pets.get(i).setTechnicalId(UUID.fromString(arrayNode.get(i).get("technicalId").asText()));
                        }
                        return pets;
                    } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error parsing Pets data");
                    }
                });
    }

    @PostMapping("/pets")
    public CompletableFuture<ResponseEntity<UUID>> createPet(@RequestBody @Valid Pet pet) {
        return entityService.addItem("Pet", ENTITY_VERSION, pet)
                .thenApply(id -> ResponseEntity.status(HttpStatus.CREATED).body(id));
    }

    @PutMapping("/pets/{id}")
    public CompletableFuture<ResponseEntity<UUID>> updatePet(@PathVariable UUID id, @RequestBody @Valid Pet pet) {
        return entityService.updateItem("Pet", ENTITY_VERSION, id, pet)
                .thenApply(updatedId -> ResponseEntity.ok(updatedId));
    }

    @DeleteMapping("/pets/{id}")
    public CompletableFuture<ResponseEntity<UUID>> deletePet(@PathVariable UUID id) {
        return entityService.deleteItem("Pet", ENTITY_VERSION, id)
                .thenApply(deletedId -> ResponseEntity.ok(deletedId));
    }

    @GetMapping("/owners/{id}")
    public CompletableFuture<ResponseEntity<Owner>> getOwner(@PathVariable UUID id) {
        return entityService.getItem("Owner", ENTITY_VERSION, id)
                .thenApply(itemNode -> {
                    if (itemNode == null || itemNode.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found");
                    }
                    try {
                        Owner owner = objectMapper.treeToValue(itemNode, Owner.class);
                        owner.setTechnicalId(UUID.fromString(itemNode.get("technicalId").asText()));
                        return ResponseEntity.ok(owner);
                    } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error parsing Owner data");
                    }
                });
    }

    @GetMapping("/owners")
    public CompletableFuture<List<Owner>> getAllOwners() {
        return entityService.getItems("Owner", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    try {
                        List<Owner> owners = objectMapper.readerForListOf(Owner.class).readValue(arrayNode);
                        for (int i = 0; i < arrayNode.size(); i++) {
                            owners.get(i).setTechnicalId(UUID.fromString(arrayNode.get(i).get("technicalId").asText()));
                        }
                        return owners;
                    } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error parsing Owners data");
                    }
                });
    }

    @PostMapping("/owners")
    public CompletableFuture<ResponseEntity<UUID>> createOwner(@RequestBody @Valid Owner owner) {
        return entityService.addItem("Owner", ENTITY_VERSION, owner)
                .thenApply(id -> ResponseEntity.status(HttpStatus.CREATED).body(id));
    }

    @PutMapping("/owners/{id}")
    public CompletableFuture<ResponseEntity<UUID>> updateOwner(@PathVariable UUID id, @RequestBody @Valid Owner owner) {
        return entityService.updateItem("Owner", ENTITY_VERSION, id, owner)
                .thenApply(updatedId -> ResponseEntity.ok(updatedId));
    }

    @DeleteMapping("/owners/{id}")
    public CompletableFuture<ResponseEntity<UUID>> deleteOwner(@PathVariable UUID id) {
        return entityService.deleteItem("Owner", ENTITY_VERSION, id)
                .thenApply(deletedId -> ResponseEntity.ok(deletedId));
    }

    @GetMapping("/appointments/{id}")
    public CompletableFuture<ResponseEntity<Appointment>> getAppointment(@PathVariable UUID id) {
        return entityService.getItem("Appointment", ENTITY_VERSION, id)
                .thenApply(itemNode -> {
                    if (itemNode == null || itemNode.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
                    }
                    try {
                        Appointment appointment = objectMapper.treeToValue(itemNode, Appointment.class);
                        appointment.setTechnicalId(UUID.fromString(itemNode.get("technicalId").asText()));
                        return ResponseEntity.ok(appointment);
                    } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error parsing Appointment data");
                    }
                });
    }

    @GetMapping("/appointments")
    public CompletableFuture<List<Appointment>> getAllAppointments() {
        return entityService.getItems("Appointment", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    try {
                        List<Appointment> appointments = objectMapper.readerForListOf(Appointment.class).readValue(arrayNode);
                        for (int i = 0; i < arrayNode.size(); i++) {
                            appointments.get(i).setTechnicalId(UUID.fromString(arrayNode.get(i).get("technicalId").asText()));
                        }
                        return appointments;
                    } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error parsing Appointments data");
                    }
                });
    }

    @PostMapping("/appointments")
    public CompletableFuture<ResponseEntity<UUID>> createAppointment(@RequestBody @Valid Appointment appointment) {
        return entityService.addItem("Appointment", ENTITY_VERSION, appointment)
                .thenApply(id -> ResponseEntity.status(HttpStatus.CREATED).body(id));
    }

    @PutMapping("/appointments/{id}")
    public CompletableFuture<ResponseEntity<UUID>> updateAppointment(@PathVariable UUID id, @RequestBody @Valid Appointment appointment) {
        return entityService.updateItem("Appointment", ENTITY_VERSION, id, appointment)
                .thenApply(updatedId -> ResponseEntity.ok(updatedId));
    }

    @DeleteMapping("/appointments/{id}")
    public CompletableFuture<ResponseEntity<UUID>> deleteAppointment(@PathVariable UUID id) {
        return entityService.deleteItem("Appointment", ENTITY_VERSION, id)
                .thenApply(deletedId -> ResponseEntity.ok(deletedId));
    }
}