package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

@Component
public class LaureateEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
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
            .validate(this::isValidEntity, "Invalid Laureate entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.getBorn() != null && !laureate.getBorn().isEmpty();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        // Enrichment logic: calculate age if born date is valid and died is null
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate bornDate = LocalDate.parse(laureate.getBorn(), formatter);
            LocalDate diedDate = null;
            if (laureate.getDied() != null && !laureate.getDied().isEmpty()) {
                diedDate = LocalDate.parse(laureate.getDied(), formatter);
            }
            int age = (diedDate == null) ? Period.between(bornDate, LocalDate.now()).getYears()
                    : Period.between(bornDate, diedDate).getYears();
            // Assuming there's a setAge method or similar to enrich data (not given in spec)
            // Since no such field exists, just log the calculated age
            logger.info("Calculated age for laureate {} {}: {} years", laureate.getFirstname(), laureate.getSurname(), age);
        } catch (Exception e) {
            logger.warn("Failed to calculate age for laureate {} {}: {}", laureate.getFirstname(), laureate.getSurname(), e.getMessage());
        }
        return laureate;
    }
}
