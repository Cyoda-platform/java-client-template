package com.java_template.application.processor;

import com.java_template.application.entity.PetAdoptionJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.JobStatusEnum;
import com.java_template.application.entity.PetStatusEnum;
import com.java_template.application.entity.RequestStatusEnum;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PetAdoptionJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    private final AtomicInteger adoptionRequestIdCounter = new AtomicInteger(1);

    public PetAdoptionJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("PetAdoptionJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetAdoptionJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PetAdoptionJob.class)
                .validate(this::isValidEntity, "Invalid PetAdoptionJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetAdoptionJobProcessor".equals(modelSpec.operationName()) &&
                "petAdoptionJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetAdoptionJob job) {
        return job != null && job.isValid();
    }

    private PetAdoptionJob processEntityLogic(PetAdoptionJob job) {
        try {
            processPetAdoptionJob(job);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error processing PetAdoptionJob with ID {}: {}", job.getId(), e.getMessage());
            job.setStatus(JobStatusEnum.FAILED);
            try {
                entityService.addItem("PetAdoptionJob", Config.ENTITY_VERSION, job).get();
            } catch (Exception ex) {
                logger.error("Error saving failed PetAdoptionJob status: {}", ex.getMessage());
            }
        }
        return job;
    }

    private void processPetAdoptionJob(PetAdoptionJob job) throws ExecutionException, InterruptedException {
        logger.info("Processing PetAdoptionJob with ID: {}", job.getId());

        // Retrieve Pet by id
        Condition petCond = Condition.of("$.id", "EQUALS", job.getPetId());
        SearchConditionRequest petCondition = SearchConditionRequest.group("AND", petCond);
        CompletableFuture<ArrayNode> petItemsFuture = entityService.getItemsByCondition("Pet", Config.ENTITY_VERSION, petCondition, true);
        ArrayNode petItems = petItemsFuture.get();

        if (petItems.isEmpty()) {
            logger.error("Pet with ID {} not found", job.getPetId());
            job.setStatus(JobStatusEnum.FAILED);
            entityService.addItem("PetAdoptionJob", Config.ENTITY_VERSION, job); // create new version with updated status
            return;
        }

        ObjectNode petNode = (ObjectNode) petItems.get(0);
        Pet pet = convertObjectNodeToPet(petNode);

        if (pet.getStatus() != PetStatusEnum.AVAILABLE) {
            logger.error("Pet with ID {} is not available for adoption", pet.getId());
            job.setStatus(JobStatusEnum.FAILED);
            entityService.addItem("PetAdoptionJob", Config.ENTITY_VERSION, job);
            return;
        }

        // Create AdoptionRequest entity
        AdoptionRequest adoptionRequest = new AdoptionRequest();
        adoptionRequest.setId("req-" + adoptionRequestIdCounter.getAndIncrement());
        adoptionRequest.setPetId(pet.getId());
        adoptionRequest.setRequesterName(job.getAdopterName());
        adoptionRequest.setRequestDate(new Date());
        adoptionRequest.setStatus(RequestStatusEnum.PENDING);

        CompletableFuture<UUID> adoptionRequestIdFuture = entityService.addItem("AdoptionRequest", Config.ENTITY_VERSION, adoptionRequest);
        adoptionRequestIdFuture.get();

        // Update pet status to ADOPTED (create new pet version)
        pet.setStatus(PetStatusEnum.ADOPTED);
        entityService.addItem("Pet", Config.ENTITY_VERSION, pet).get();

        // Update job status to COMPLETED (create new job version)
        job.setStatus(JobStatusEnum.COMPLETED);
        entityService.addItem("PetAdoptionJob", Config.ENTITY_VERSION, job).get();

        logger.info("PetAdoptionJob {} processed successfully", job.getId());
    }

    private Pet convertObjectNodeToPet(ObjectNode petNode) {
        Pet pet = new Pet();
        if (petNode.hasNonNull("id")) {
            pet.setId(petNode.get("id").asText());
        }
        if (petNode.hasNonNull("name")) {
            pet.setName(petNode.get("name").asText());
        }
        if (petNode.hasNonNull("category")) {
            pet.setCategory(petNode.get("category").asText());
        }
        if (petNode.hasNonNull("status")) {
            pet.setStatus(PetStatusEnum.valueOf(petNode.get("status").asText()));
        }
        return pet;
    }
}
