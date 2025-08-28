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
import java.time.format.DateTimeParseException;

@Component
public class FieldFormatCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FieldFormatCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FieldFormatCriterion(SerializerFactory serializerFactory) {
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

        boolean allFormatsOk = true;

        // Validate year: must be a 4-digit integer within reasonable range
        String year = entity.getYear();
        if (year == null || year.isBlank()) {
            logger.warn("Laureate {} has missing year", entity.getId());
            allFormatsOk = false;
        } else {
            try {
                int y = Integer.parseInt(year);
                if (year.length() != 4 || y < 1800 || y > 2100) {
                    logger.warn("Laureate {} has invalid year value: {}", entity.getId(), year);
                    allFormatsOk = false;
                }
            } catch (NumberFormatException ex) {
                logger.warn("Laureate {} year is not a number: {}", entity.getId(), year);
                allFormatsOk = false;
            }
        }

        // Validate born date format (yyyy-MM-dd) if present
        String born = entity.getBorn();
        if (born != null && !born.isBlank()) {
            try {
                LocalDate.parse(born);
            } catch (DateTimeParseException ex) {
                logger.warn("Laureate {} has invalid born date format: {}", entity.getId(), born);
                allFormatsOk = false;
            }
        }

        // Validate died date format (yyyy-MM-dd) if present (died may be null)
        String died = entity.getDied();
        if (died != null && !died.isBlank()) {
            try {
                LocalDate.parse(died);
            } catch (DateTimeParseException ex) {
                logger.warn("Laureate {} has invalid died date format: {}", entity.getId(), died);
                allFormatsOk = false;
            }
        }

        // Validate born country code: if present must be two alphabetic characters
        String bornCountryCode = entity.getBornCountryCode();
        if (bornCountryCode != null && !bornCountryCode.isBlank()) {
            String code = bornCountryCode.trim();
            if (code.length() == 2 && code.matches("[A-Za-z]{2}")) {
                String normalized = code.toUpperCase();
                entity.setNormalizedCountryCode(normalized);
            } else {
                logger.warn("Laureate {} has invalid bornCountryCode: {}", entity.getId(), bornCountryCode);
                allFormatsOk = false;
            }
        }

        // Validate gender if present: accept common values
        String gender = entity.getGender();
        if (gender != null && !gender.isBlank()) {
            String g = gender.trim().toLowerCase();
            if (!(g.equals("male") || g.equals("female") || g.equals("other") || g.equals("unknown"))) {
                logger.warn("Laureate {} has unexpected gender value: {}", entity.getId(), gender);
                // Not strictly fatal but mark as format issue
                allFormatsOk = false;
            }
        }

        // Set validation flag based on format checks
        if (allFormatsOk) {
            entity.setValidated("VALIDATED");
            logger.info("Laureate {} field formats validated", entity.getId());
        } else {
            entity.setValidated("INVALID");
            logger.info("Laureate {} field formats invalid", entity.getId());
        }

        return entity;
    }
}