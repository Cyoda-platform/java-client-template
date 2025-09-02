package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class CatFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CatFact entity) {
        return entity != null && entity.isValid();
    }

    private CatFact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFact> context) {
        CatFact entity = context.entity();
        
        // Set retrieved date if not already set (for new facts)
        if (entity.getRetrievedDate() == null) {
            entity.setRetrievedDate(LocalDateTime.now());
        }
        
        // Initialize usage count if not set
        if (entity.getUsageCount() == null) {
            entity.setUsageCount(0);
        }
        
        // Set source if not provided
        if (entity.getSource() == null || entity.getSource().trim().isEmpty()) {
            entity.setSource("catfact.ninja");
        }
        
        // Generate ID if not set
        if (entity.getId() == null) {
            entity.setId("fact-" + UUID.randomUUID().toString().substring(0, 8));
        }
        
        // Set length based on fact text
        if (entity.getFactText() != null) {
            entity.setLength(entity.getFactText().length());
            
            // Clean up the fact text (remove excessive whitespace)
            entity.setFactText(entity.getFactText().trim().replaceAll("\\s+", " "));
        }
        
        // In a real implementation, we would:
        // 1. Call the Cat Fact API to fetch new facts
        // 2. Validate content quality
        // 3. Check for duplicates
        // 4. Apply content filters
        
        logger.info("Processed cat fact: {} with length: {}", entity.getId(), entity.getLength());
        return entity;
    }
}
