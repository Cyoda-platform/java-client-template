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

import java.time.Year;
import java.util.HashMap;
import java.util.Map;

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
        logger.info("Processing Laureate validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate laureate = context.entity();
        Map<String,String> validations = laureate.getValidations();
        if (validations == null) validations = new HashMap<>();

        // Validate required fields
        if (laureate.getId() == null) {
            validations.put("id", "source id is required");
        }
        if ((laureate.getFirstname() == null || laureate.getFirstname().isBlank()) && (laureate.getSurname() == null || laureate.getSurname().isBlank()) && (laureate.getName() == null || laureate.getName().isBlank())) {
            validations.put("name", "either firstname/surname or affiliation name is required");
        }
        if (laureate.getYear() == null || laureate.getYear().isBlank()) {
            validations.put("year", "award year is required");
        } else {
            // check year format
            try {
                int y = Integer.parseInt(laureate.getYear());
                int current = Year.now().getValue();
                if (y < 1800 || y > current + 1) {
                    validations.put("year", "award year out of plausible range");
                }
            } catch (NumberFormatException nfe) {
                validations.put("year", "award year is not a valid number");
            }
        }
        if (laureate.getCategory() == null || laureate.getCategory().isBlank()) {
            validations.put("category", "category is required");
        }

        // Date format checks (born/died) - simple ISO-8601 substring checks
        if (laureate.getBorn() != null && !laureate.getBorn().isBlank()) {
            if (!laureate.getBorn().matches("^\\d{4}-\\d{2}-\\d{2}.*$")) {
                validations.put("born", "born date not in ISO-8601 format");
            }
        }
        if (laureate.getDied() != null && !laureate.getDied().isBlank()) {
            if (!laureate.getDied().matches("^\\d{4}-\\d{2}-\\d{2}.*$")) {
                validations.put("died", "died date not in ISO-8601 format");
            }
        }

        // If any critical validation errors exist, mark as REJECTED
        boolean hasCritical = validations.containsKey("id") || validations.containsKey("name") || validations.containsKey("year") || validations.containsKey("category");
        if (hasCritical) {
            laureate.setStatus("REJECTED");
        } else {
            laureate.setStatus("VALIDATED");
        }

        laureate.setValidations(validations);
        logger.info("Validation completed for laureate id={} status={} issues={}", laureate.getId(), laureate.getStatus(), validations.size());
        return laureate;
    }
}
