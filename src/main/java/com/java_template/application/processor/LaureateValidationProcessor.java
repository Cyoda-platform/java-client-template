package com.java_template.application.processor;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
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
import java.time.Year;
import java.time.format.DateTimeParseException;

@Component
public class LaureateValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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

        // Default to valid, then mark invalid if any check fails
        boolean valid = true;
        String reason = null;

        // ID should be numeric (original source id). Laureate.id is required per isValid(), but ensure numeric.
        String id = entity.getId();
        if (id == null || id.isBlank()) {
            valid = false;
            reason = "missing id";
        } else {
            try {
                Integer.parseInt(id);
            } catch (NumberFormatException e) {
                valid = false;
                reason = "id is not numeric";
            }
        }

        // Year should be a valid integer and within a sensible range
        if (valid) {
            String yearStr = entity.getYear();
            if (yearStr == null || yearStr.isBlank()) {
                valid = false;
                reason = "missing year";
            } else {
                try {
                    int y = Integer.parseInt(yearStr);
                    int current = Year.now().getValue();
                    if (y < 1800 || y > current + 1) {
                        valid = false;
                        reason = "year out of range";
                    }
                } catch (NumberFormatException e) {
                    valid = false;
                    reason = "year is not numeric";
                }
            }
        }

        // born and died should be ISO date strings (yyyy-MM-dd) if present
        if (valid) {
            String born = entity.getBorn();
            if (born != null && !born.isBlank()) {
                try {
                    LocalDate.parse(born); // ISO_LOCAL_DATE
                } catch (DateTimeParseException e) {
                    valid = false;
                    reason = "born date has invalid format";
                }
            }
        }

        if (valid) {
            String died = entity.getDied();
            if (died != null && !died.isBlank()) {
                try {
                    LocalDate diedDate = LocalDate.parse(died);
                    String born = entity.getBorn();
                    if (born != null && !born.isBlank()) {
                        try {
                            LocalDate bornDate = LocalDate.parse(born);
                            if (diedDate.isBefore(bornDate)) {
                                valid = false;
                                reason = "died date is before born date";
                            }
                        } catch (DateTimeParseException e) {
                            // born date parse error handled above; if it occurs here, mark invalid
                            valid = false;
                            reason = "born date has invalid format";
                        }
                    }
                } catch (DateTimeParseException e) {
                    valid = false;
                    reason = "died date has invalid format";
                }
            }
        }

        // Basic country code normalization check: if present, should be 2-letter or 3-letter
        if (valid) {
            String cc = entity.getBornCountryCode();
            if (cc != null && !cc.isBlank()) {
                String trimmed = cc.trim();
                if (!(trimmed.length() == 2 || trimmed.length() == 3)) {
                    valid = false;
                    reason = "bornCountryCode length invalid";
                } else {
                    // normalize to upper-case ISO-like code for storage
                    entity.setNormalizedCountryCode(trimmed.toUpperCase());
                }
            } else {
                // ensure normalizedCountryCode is null if no code provided
                entity.setNormalizedCountryCode(null);
            }
        }

        // Finalize validation flag
        if (valid) {
            entity.setValidated("VALIDATED");
            logger.info("Laureate {} validated successfully", entity.getId());
        } else {
            entity.setValidated("INVALID");
            logger.warn("Laureate {} validation failed: {}", entity != null ? entity.getId() : "unknown", reason);
        }

        return entity;
    }
}