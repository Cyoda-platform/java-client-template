package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Validated
@RestController
@RequestMapping(path = "/entity/pets")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "Pet";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/fetch")
    public ResponseEntity<FetchPetsResponse> fetchPets(@RequestBody @Valid FetchPetsRequest request) throws ExecutionException, InterruptedException {
        String statusFilter = request.getStatus() != null && !request.getStatus().isBlank()
                ? request.getStatus() : "available";
        logger.info("Fetching pets with status={}", statusFilter);

        // Delegate business logic to entityService or workflow processor (not implemented here)
        // For example: List<Pet> pets = petWorkflow.fetchPetsByStatus(statusFilter);
        throw new UnsupportedOperationException("Business logic should be implemented in processors or services");
    }

    @GetMapping
    public ResponseEntity<GetPetsResponse> getPets() throws ExecutionException, InterruptedException {
        logger.info("Retrieving all pets via EntityService");

        // Delegate to entityService or processor
        throw new UnsupportedOperationException("Business logic should be implemented in processors or services");
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptPetResponse> adoptPet(@RequestBody @Valid AdoptPetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Adopt request for petId={}", request.getPetId());

        // Delegate adoption logic to entityService or processor
        throw new UnsupportedOperationException("Business logic should be implemented in processors or services");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        ErrorResponse error = new ErrorResponse(org.springframework.http.HttpStatus.BAD_REQUEST.toString(), msg);
        return new ResponseEntity<>(error, org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchPetsRequest {
        private String type;
        private String status;
    }

    @Data
    public static class FetchPetsResponse {
        private List<Pet> pets;
    }

    @Data
    public static class GetPetsResponse {
        private List<Pet> pets;
    }

    @Data
    public static class AdoptPetRequest {
        @NotNull
        private UUID petId;
    }

    @Data
    @AllArgsConstructor
    public static class AdoptPetResponse {
        private String message;
        private Pet pet;
    }

    @Data
    @NoArgsConstructor
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String name;
        private String type;
        private String status;
        private String description;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}
