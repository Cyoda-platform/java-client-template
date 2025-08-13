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
                .validate(this::isValidEntity, "Invalid Laureate entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        // Basic null check for mandatory fields to allow enrichment
        return entity != null && entity.getFirstname() != null && entity.getSurname() != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        try {
            // Example enrichment: Calculate age if born and died dates are present
            if (entity.getBorn() != null) {
                LocalDate bornDate = LocalDate.parse(entity.getBorn());
                LocalDate diedDate = null;
                if (entity.getDied() != null) {
                    diedDate = LocalDate.parse(entity.getDied());
                }
                int age;
                if (diedDate != null) {
                    age = Period.between(bornDate, diedDate).getYears();
                } else {
                    age = Period.between(bornDate, LocalDate.now()).getYears();
                }
                // Assuming entity has a setAge method or similar,
                // but as per instructions, do not invent properties.
                // So we might log or prepare for future enrichment.
                logger.info("Calculated age for laureate {}: {} years", entity.getFirstname(), age);
            }

            // Additional enrichment can be added here, e.g., normalize country codes
            if (entity.getBorncountrycode() != null) {
                String normalizedCountryCode = entity.getBorncountrycode().toUpperCase();
                // No setter available as per instructions; just log enrichment
                logger.info("Normalized country code for laureate {}: {}", entity.getFirstname(), normalizedCountryCode);
            }

        } catch (DateTimeParseException e) {
            logger.error("Date parsing error during enrichment for laureate {}: {}", entity.getFirstname(), e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during enrichment for laureate {}: {}", entity.getFirstname(), e.getMessage());
        }
        return entity;
    }
}
