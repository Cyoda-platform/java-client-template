package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class EnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Laureate.class)
                .validate(this::isValidEntity, "Invalid laureate entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.getLaureateId() != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        // Enrichment logic
        // Calculate age if born and died are present
        try {
            if (entity.getBorn() != null) {
                LocalDate birthDate = LocalDate.parse(entity.getBorn());
                LocalDate deathDate = entity.getDied() != null ? LocalDate.parse(entity.getDied()) : LocalDate.now();
                int age = Period.between(birthDate, deathDate).getYears();
                // Assuming Laureate class has a method to set age or an enrichment field, which is not defined in specs
                // So cannot set age explicitly, just log for now
                logger.debug("Calculated age for laureateId {}: {}", entity.getLaureateId(), age);
            }
            // Normalize country codes to uppercase
            if (entity.getBorncountrycode() != null) {
                String normalizedCode = entity.getBorncountrycode().toUpperCase();
                // No setter, just log normalization
                logger.debug("Normalized borncountrycode for laureateId {}: {}", entity.getLaureateId(), normalizedCode);
            }
        } catch (DateTimeParseException e) {
            logger.error("Date parsing error during enrichment for laureateId {}: {}", entity.getLaureateId(), e.getMessage());
        }
        return entity;
    }
}
