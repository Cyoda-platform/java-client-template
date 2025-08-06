package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class PetIngestionProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public PetIngestionProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(pet -> pet.getId() != null && pet.getName() != null && !pet.getName().isBlank(), "Invalid pet data")
            .map(this::processPetLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private Pet processPetLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        // Example business logic:
        // - Validate pet data structure
        // - Possibly enrich pet data
        // Since we do not have detailed prototype logic, we only validate here
        // No changes to pet entity are made
        return pet;
    }
}
