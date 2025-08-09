package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1000.Laureate;
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

@Component
public class LaureateEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateEnrichmentProcessor.class);
    private final ProcessorSerializer serializer;

    public LaureateEnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid Laureate entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "LaureateEnrichmentProcessor".equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.getBorncountrycode() != null && !entity.getBorncountrycode().isEmpty();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        // Normalize country code to uppercase
        String countryCode = entity.getBorncountrycode();
        if (countryCode != null) {
            entity.setBorncountrycode(countryCode.toUpperCase());
        }

        // Calculate age at award if born and year are present
        try {
            if (entity.getBorn() != null && entity.getYear() != null && !entity.getYear().isEmpty()) {
                LocalDate bornDate = LocalDate.parse(entity.getBorn().toString());
                int awardYear = Integer.parseInt(entity.getYear());
                int ageAtAward = awardYear - bornDate.getYear();
                // Assuming Laureate has a setAgeAtAward property (not in original, so skip storing)
                logger.info("Calculated age at award for {} {}: {}", entity.getFirstname(), entity.getSurname(), ageAtAward);
            }
        } catch (Exception e) {
            logger.warn("Failed to calculate age at award for laureate {} {}", entity.getFirstname(), entity.getSurname());
        }

        return entity;
    }
}
