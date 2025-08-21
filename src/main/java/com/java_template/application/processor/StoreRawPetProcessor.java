package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.rawpet.version_1.RawPet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class StoreRawPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoreRawPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StoreRawPetProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StoreRawPet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(RawPet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(RawPet entity) {
        return entity != null && ("RAW_CREATED".equals(entity.getState()) || entity.getState() == null);
    }

    private RawPet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<RawPet> context) {
        RawPet entity = context.entity();
        try {
            // Ensure payload is present
            if (entity.getPayload() == null || entity.getPayload().isEmpty()) {
                entity.setState("RAW_FAILED");
                logger.warn("RawPet missing payload, marking RAW_FAILED");
                return entity;
            }
            // Set ingestedAt and default state to STORED
            entity.setIngestedAt(Instant.now().toString());
            entity.setState("STORED");
            logger.info("RawPet {} stored", entity.getRawId() == null ? "<new>" : entity.getRawId());
        } catch (Exception e) {
            logger.error("Error storing RawPet", e);
            entity.setState("RAW_FAILED");
        }
        return entity;
    }
}
