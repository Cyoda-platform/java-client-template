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
        // Validation for Laureate during validation step:
        // Required: id, firstname, surname, year, category
        if (entity == null) return false;
        if (entity.getId() == null) return false;
        if (entity.getFirstname() == null || entity.getFirstname().isBlank()) return false;
        if (entity.getSurname() == null || entity.getSurname().isBlank()) return false;
        if (entity.getYear() == null || entity.getYear().isBlank()) return false;
        if (entity.getCategory() == null || entity.getCategory().isBlank()) return false;
        // year should be a 4-digit numeric string (basic format check)
        String year = entity.getYear().trim();
        if (!year.matches("\\d{4}")) return false;
        return true;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        // Defensive null-check (shouldn't happen due to validate above)
        if (entity == null) return null;

        // Normalize and trim textual fields
        if (entity.getFirstname() != null) {
            entity.setFirstname(entity.getFirstname().trim());
        }
        if (entity.getSurname() != null) {
            entity.setSurname(entity.getSurname().trim());
        }
        if (entity.getCategory() != null) {
            entity.setCategory(entity.getCategory().trim());
        }
        if (entity.getMotivation() != null) {
            entity.setMotivation(entity.getMotivation().trim());
        }
        if (entity.getBorn() != null) {
            entity.setBorn(entity.getBorn().trim());
        }
        if (entity.getDied() != null) {
            entity.setDied(entity.getDied().trim());
        }
        if (entity.getBornCity() != null) {
            entity.setBornCity(entity.getBornCity().trim());
        }
        if (entity.getBornCountry() != null) {
            entity.setBornCountry(entity.getBornCountry().trim());
        }
        if (entity.getBornCountryCode() != null) {
            entity.setBornCountryCode(entity.getBornCountryCode().trim().toUpperCase());
        }
        if (entity.getAffiliationName() != null) {
            entity.setAffiliationName(entity.getAffiliationName().trim());
        }
        if (entity.getAffiliationCity() != null) {
            entity.setAffiliationCity(entity.getAffiliationCity().trim());
        }
        if (entity.getAffiliationCountry() != null) {
            entity.setAffiliationCountry(entity.getAffiliationCountry().trim());
        }
        if (entity.getGender() != null) {
            entity.setGender(entity.getGender().trim());
        }
        if (entity.getYear() != null) {
            entity.setYear(entity.getYear().trim());
        }

        // Re-validate critical fields and set recordStatus to INVALID when validation fails
        boolean valid = true;
        if (entity.getId() == null) valid = false;
        if (entity.getFirstname() == null || entity.getFirstname().isBlank()) valid = false;
        if (entity.getSurname() == null || entity.getSurname().isBlank()) valid = false;
        if (entity.getCategory() == null || entity.getCategory().isBlank()) valid = false;
        if (entity.getYear() == null || entity.getYear().isBlank()) valid = false;
        if (entity.getYear() != null && !entity.getYear().matches("\\d{4}")) valid = false;

        if (!valid) {
            // Mark record as invalid so downstream workflow can transition to INVALID state
            entity.setRecordStatus("INVALID");
            logger.warn("Laureate validation failed for id={}, marking as INVALID", entity.getId());
            return entity;
        }

        // If valid, do not change recordStatus here (classification will be done by DeduplicationProcessor).
        // Ensure recordStatus is not blank; if absent leave as-is to be handled later.
        logger.info("Laureate validation passed for id={}", entity.getId());
        return entity;
    }
}