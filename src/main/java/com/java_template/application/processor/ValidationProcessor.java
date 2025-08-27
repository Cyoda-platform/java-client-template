package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ValidationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        // Basic gate: entity must be present and have an id (source id)
        return entity != null && entity.getId() != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        List<String> errors = new ArrayList<>();

        // Validate id
        if (entity.getId() == null) {
            errors.add("missing id");
        }

        // Validate name: require at least firstname or surname
        boolean hasFirst = entity.getFirstname() != null && !entity.getFirstname().isBlank();
        boolean hasLast = entity.getSurname() != null && !entity.getSurname().isBlank();
        if (!hasFirst && !hasLast) {
            errors.add("missing name");
        }

        // Validate category
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            errors.add("missing category");
        }

        // Validate year: must be present and numeric (simple check)
        if (entity.getYear() == null || entity.getYear().isBlank()) {
            errors.add("missing year");
        } else {
            String year = entity.getYear().trim();
            if (!year.matches("\\d{4}")) {
                errors.add("invalid year");
            }
        }

        // Validate born date if present: should be parseable ISO date
        if (entity.getBorn() != null && !entity.getBorn().isBlank()) {
            try {
                LocalDate.parse(entity.getBorn());
            } catch (DateTimeParseException ex) {
                errors.add("invalid born date");
            }
        }

        // Validate born country code if present: prefer 2-letter code
        if (entity.getBornCountryCode() != null && !entity.getBornCountryCode().isBlank()) {
            String code = entity.getBornCountryCode().trim();
            if (code.length() != 2) {
                errors.add("invalid born country code");
            }
        }

        // Compose validationStatus
        if (errors.isEmpty()) {
            entity.setValidationStatus("VALID");
        } else {
            entity.setValidationStatus("INVALID:" + String.join("; ", errors));
        }

        logger.info("Laureate [{}] validation result: {}", entity.getId(), entity.getValidationStatus());
        return entity;
    }
}