package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CompleteAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CompleteAdoptionProcessor(SerializerFactory serializerFactory,
                                     EntityService entityService,
                                     ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest entity = context.entity();
        try {
            // Only act when adoption request has been approved (transition to completion)
            String status = entity.getStatus();
            if (status == null) {
                logger.info("AdoptionRequest {} has no status, skipping completion", entity.getId());
                return entity;
            }

            // If already completed, nothing to do
            if ("completed".equalsIgnoreCase(status)) {
                logger.info("AdoptionRequest {} already completed", entity.getId());
                return entity;
            }

            // Business rule:
            // - When completing an approved adoption request, mark the request status to COMPLETED,
            //   set decisionDate to now and update the related Pet status to 'adopted'.
            // - We are not allowed to call update on the triggering entity (AdoptionRequest);
            //   simply modify it and return — Cyoda will persist it.
            // - We may update other entities (Pet, Owner) via EntityService.

            // Only proceed when request was approved (or if operator calls complete regardless)
            if (!"approved".equalsIgnoreCase(status) && !"approved".equalsIgnoreCase(entity.getStatus())) {
                // If not approved, still allow explicit completion only if status equals "approved" or "under_review" moved to approved earlier.
                // Otherwise skip.
                logger.info("AdoptionRequest {} not approved (status={}), skipping pet adoption update", entity.getId(), status);
                return entity;
            }

            // Set adoption request final fields
            entity.setStatus("completed");
            entity.setDecisionDate(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

            // Update Pet status to 'adopted'
            try {
                if (entity.getPetId() != null && !entity.getPetId().isBlank()) {
                    UUID petUuid = UUID.fromString(entity.getPetId());
                    ObjectNode petNode = entityService.getItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), petUuid).join();
                    if (petNode != null) {
                        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
                        if (pet != null) {
                            pet.setStatus("adopted");
                            // Persist change to pet (allowed - updating other entities)
                            entityService.updateItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), UUID.fromString(pet.getId()), pet).join();
                            logger.info("Pet {} marked as adopted for AdoptionRequest {}", pet.getId(), entity.getId());
                        }
                    } else {
                        logger.warn("Pet not found for id {} while completing AdoptionRequest {}", entity.getPetId(), entity.getId());
                    }
                } else {
                    logger.warn("AdoptionRequest {} has empty petId", entity.getId());
                }
            } catch (Exception e) {
                logger.error("Failed to update Pet for AdoptionRequest " + entity.getId(), e);
            }

            // Optionally, add pet to owner's favorites (helps UX). Safe to update Owner.
            try {
                if (entity.getOwnerId() != null && !entity.getOwnerId().isBlank()) {
                    UUID ownerUuid = UUID.fromString(entity.getOwnerId());
                    ObjectNode ownerNode = entityService.getItem(Owner.ENTITY_NAME, String.valueOf(Owner.ENTITY_VERSION), ownerUuid).join();
                    if (ownerNode != null) {
                        Owner owner = objectMapper.treeToValue(ownerNode, Owner.class);
                        if (owner != null) {
                            List<String> favs = owner.getFavorites();
                            if (favs == null) favs = new ArrayList<>();
                            if (entity.getPetId() != null && !favs.contains(entity.getPetId())) {
                                favs.add(entity.getPetId());
                                owner.setFavorites(favs);
                                entityService.updateItem(Owner.ENTITY_NAME, String.valueOf(Owner.ENTITY_VERSION), UUID.fromString(owner.getId()), owner).join();
                                logger.info("Added pet {} to favorites of owner {}", entity.getPetId(), owner.getId());
                            }
                        }
                    } else {
                        logger.warn("Owner not found for id {} while completing AdoptionRequest {}", entity.getOwnerId(), entity.getId());
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to update Owner for AdoptionRequest " + entity.getId(), e);
            }

        } catch (Exception ex) {
            logger.error("Error while processing AdoptionRequest " + entity.getId(), ex);
        }

        return entity;
    }
}