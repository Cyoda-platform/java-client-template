package com.java_template.application.processor;

import com.java_template.application.entity.workflow.version_1.Workflow;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

@Component
public class ProcessWorkflowProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessWorkflowProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessWorkflowProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Workflow for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Workflow.class)
            .validate(this::isValidEntity, "Invalid Workflow state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Workflow entity) {
        return entity != null && entity.getName() != null && !entity.getName().isEmpty() && entity.getInputPetData() != null && !entity.getInputPetData().isEmpty();
    }

    private Workflow processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Workflow> context) {
        Workflow workflow = context.entity();

        // Update status to RUNNING
        workflow.setStatus("RUNNING");
        logger.info("Workflow status set to RUNNING for id: {}", workflow.getTechnicalId());

        // Parse inputPetData JSON string to create Pet entities
        // Assuming inputPetData is a JSON array of pet objects
        String inputPetData = workflow.getInputPetData();

        // Pseudo parse - in real implementation, JSON parsing library would be used
        // For demonstration, split by '},{' and parse minimal fields
        List<String> petDataList = new ArrayList<>();
        if (inputPetData != null && !inputPetData.isEmpty()) {
            // Remove leading and trailing square brackets if present
            String trimmed = inputPetData.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            // Split into individual pet JSON strings
            petDataList = Arrays.asList(trimmed.split("},\s*\{"));
        }

        // For each pet data string, create and process a Pet entity
        for (String petDataRaw : petDataList) {
            Pet pet = new Pet();
            // Minimal parsing just to set petId and name from raw string
            // In real code, use Jackson or Gson to parse JSON properly
            // This is a placeholder to simulate ingestion
            if (petDataRaw.contains("petId")) {
                // Extract petId value
                int index = petDataRaw.indexOf("petId");
                int start = petDataRaw.indexOf(":", index) + 2;
                int end = petDataRaw.indexOf("\"", start);
                String petId = petDataRaw.substring(start, end);
                pet.setPetId(petId);
            }
            if (petDataRaw.contains("name")) {
                int index = petDataRaw.indexOf("name");
                int start = petDataRaw.indexOf(":", index) + 2;
                int end = petDataRaw.indexOf("\"", start);
                String name = petDataRaw.substring(start, end);
                pet.setName(name);
            }
            // Set status to available by default
            pet.setStatus("available");
            pet.setCategory("unknown");
            pet.setPhotoUrls("");
            pet.setTags("");
            pet.setCreatedAt(java.time.Instant.now().toString());

            // Trigger pet processing - in real implementation, would send event
            logger.info("Processing Pet ingestion for petId: {}", pet.getPetId());
            // Here we simulate processing by just logging
        }

        // After processing all pets, set status to COMPLETED
        workflow.setStatus("COMPLETED");
        logger.info("Workflow processing completed for id: {}", workflow.getTechnicalId());

        return workflow;
    }
}
