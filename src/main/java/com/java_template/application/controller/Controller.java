package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;

import com.java_template.application.entity.workflow.version_1.Workflow;
import com.java_template.application.entity.pet.version_1.Pet;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api")
@Tag(name = "Purrfect Pets API Controller", description = "Event-Driven REST API controller for Workflow and Pet entities")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /workflow - Create a new Workflow entity and trigger processWorkflow event
    @Operation(summary = "Create Workflow", description = "Creates a new Workflow entity and triggers processing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workflow created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/workflow")
    public ResponseEntity<?> createWorkflow(@RequestBody WorkflowRequest request) {
        try {
            Workflow workflow = new Workflow();
            workflow.setName(request.getName());
            workflow.setDescription(request.getDescription());
            workflow.setInputPetData(request.getInputPetData());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Workflow.ENTITY_NAME,
                Workflow.ENTITY_VERSION,
                workflow
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse response = new TechnicalIdResponse(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /workflow/{technicalId} - Retrieve Workflow entity by technicalId
    @Operation(summary = "Get Workflow", description = "Retrieve Workflow entity by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Workflow found", content = @Content(schema = @Schema(implementation = WorkflowResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Workflow not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/workflow/{technicalId}")
    public ResponseEntity<?> getWorkflow(
        @Parameter(name = "technicalId", description = "Technical ID of the Workflow")
        @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                UUID.fromString(technicalId)
            );

            ObjectNode workflowNode = itemFuture.get();
            if (workflowNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Workflow not found");
            }

            WorkflowResponse response = new WorkflowResponse();
            response.setName(workflowNode.path("name").asText(null));
            response.setDescription(workflowNode.path("description").asText(null));
            response.setStatus(workflowNode.path("status").asText(null));
            response.setCreatedAt(workflowNode.path("createdAt").asText(null));
            response.setInputPetData(workflowNode.path("inputPetData").asText(null));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /pet/{technicalId} - Retrieve Pet entity by technicalId
    @Operation(summary = "Get Pet", description = "Retrieve Pet entity by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Pet found", content = @Content(schema = @Schema(implementation = PetResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Pet not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/pet/{technicalId}")
    public ResponseEntity<?> getPet(
        @Parameter(name = "technicalId", description = "Technical ID of the Pet")
        @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                UUID.fromString(technicalId)
            );

            ObjectNode petNode = itemFuture.get();
            if (petNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
            }

            PetResponse response = new PetResponse();
            response.setPetId(petNode.path("petId").asText(null));
            response.setName(petNode.path("name").asText(null));
            response.setCategory(petNode.path("category").asText(null));
            response.setStatus(petNode.path("status").asText(null));
            response.setPhotoUrls(petNode.path("photoUrls").asText(null));
            response.setTags(petNode.path("tags").asText(null));
            response.setCreatedAt(petNode.path("createdAt").asText(null));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException error", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // Static DTO classes for requests and responses

    @io.swagger.v3.oas.annotations.media.Schema(description = "Request DTO for creating a Workflow")
    public static class WorkflowRequest {
        @Schema(description = "Name of the workflow", required = true)
        private String name;

        @Schema(description = "Description of the workflow", required = false)
        private String description;

        @Schema(description = "Input Pet Data (raw or reference)", required = true)
        private String inputPetData;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getInputPetData() {
            return inputPetData;
        }

        public void setInputPetData(String inputPetData) {
            this.inputPetData = inputPetData;
        }
    }

    @io.swagger.v3.oas.annotations.media.Schema(description = "Response DTO for Workflow retrieval")
    public static class WorkflowResponse {
        @Schema(description = "Name of the workflow")
        private String name;

        @Schema(description = "Description of the workflow")
        private String description;

        @Schema(description = "Current status of the workflow")
        private String status;

        @Schema(description = "Creation timestamp of the workflow")
        private String createdAt;

        @Schema(description = "Input Pet Data (raw or reference)")
        private String inputPetData;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getInputPetData() {
            return inputPetData;
        }

        public void setInputPetData(String inputPetData) {
            this.inputPetData = inputPetData;
        }
    }

    @io.swagger.v3.oas.annotations.media.Schema(description = "Response DTO for Pet retrieval")
    public static class PetResponse {
        @Schema(description = "Petstore API pet identifier")
        private String petId;

        @Schema(description = "Name of the pet")
        private String name;

        @Schema(description = "Category/type of pet")
        private String category;

        @Schema(description = "Availability status")
        private String status;

        @Schema(description = "Comma-separated URLs of pet photos")
        private String photoUrls;

        @Schema(description = "Comma-separated tags related to the pet")
        private String tags;

        @Schema(description = "Timestamp of pet record creation")
        private String createdAt;

        public String getPetId() {
            return petId;
        }

        public void setPetId(String petId) {
            this.petId = petId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getPhotoUrls() {
            return photoUrls;
        }

        public void setPhotoUrls(String photoUrls) {
            this.photoUrls = photoUrls;
        }

        public String getTags() {
            return tags;
        }

        public void setTags(String tags) {
            this.tags = tags;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    @io.swagger.v3.oas.annotations.media.Schema(description = "Response DTO containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getTechnicalId() {
            return technicalId;
        }
    }
}