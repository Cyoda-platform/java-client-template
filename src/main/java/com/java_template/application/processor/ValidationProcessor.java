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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class ValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        List<String> errors = new ArrayList<>();

        // Check id
        if (entity.getId() == null) {
            errors.add("id is missing");
        }

        // Name checks
        if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
            errors.add("firstname is missing or blank");
        }
        if (entity.getSurname() == null || entity.getSurname().isBlank()) {
            errors.add("surname is missing or blank");
        }

        // Category and year checks
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            errors.add("category is missing or blank");
        }
        if (entity.getYear() == null || entity.getYear().isBlank()) {
            errors.add("year is missing or blank");
        } else {
            // optional: ensure year looks like a number (YYYY)
            String yearStr = entity.getYear().trim();
            if (!yearStr.matches("^\\d{4}$")) {
                errors.add("year must be a 4-digit year");
            }
        }

        // Date validations for born and died (if present)
        if (entity.getBorn() == null || entity.getBorn().isBlank()) {
            errors.add("born date is missing or blank");
        } else {
            try {
                LocalDate.parse(entity.getBorn());
            } catch (DateTimeParseException e) {
                errors.add("born date is not a valid ISO date (yyyy-MM-dd)");
            }
        }

        if (entity.getDied() != null && !entity.getDied().isBlank()) {
            try {
                LocalDate.parse(entity.getDied());
            } catch (DateTimeParseException e) {
                errors.add("died date is not a valid ISO date (yyyy-MM-dd)");
            }
        }

        // Motivation: if present, should not be blank
        if (entity.getMotivation() != null && entity.getMotivation().isBlank()) {
            errors.add("motivation is blank");
        }

        // Country code normalization hint: ensure borncountrycode if present is 2-letter
        if (entity.getBorncountrycode() != null && !entity.getBorncountrycode().isBlank()) {
            String cc = entity.getBorncountrycode().trim();
            if (!cc.matches("^[A-Za-z]{2}$")) {
                errors.add("borncountrycode should be a 2-letter country code");
            }
        }

        // Set validation results on entity
        if (errors.isEmpty()) {
            entity.setValidationStatus("OK");
            entity.setValidationErrors(null);
        } else {
            entity.setValidationStatus("INVALID");
            entity.setValidationErrors(errors);
        }

        // Log outcome
        if (Objects.equals(entity.getValidationStatus(), "OK")) {
            logger.info("Laureate {} validated successfully", entity.getId());
        } else {
            logger.warn("Laureate {} validation failed: {}", entity.getId(), entity.getValidationErrors());
        }

        return entity;
    }
}