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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IndexingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IndexingProcessor(SerializerFactory serializerFactory) {
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
        if (entity == null) return null;

        try {
            // Ensure validation status exists: if missing, infer from entity.isValid()
            if (entity.getValidated() == null || entity.getValidated().isBlank()) {
                if (entity.isValid()) {
                    entity.setValidated("VALIDATED");
                } else {
                    entity.setValidated("INVALID");
                    logger.info("Laureate marked INVALID by IndexingProcessor (missing required fields). id={}", entity.getId());
                    return entity; // stop processing invalid laureate
                }
            }

            // If already invalid, nothing to do
            if ("INVALID".equalsIgnoreCase(entity.getValidated())) {
                logger.info("Laureate is INVALID, skipping indexing logic. id={}", entity.getId());
                return entity;
            }

            // Trim textual fields to normalize data
            if (entity.getFirstname() != null) entity.setFirstname(entity.getFirstname().trim());
            if (entity.getSurname() != null) entity.setSurname(entity.getSurname().trim());
            if (entity.getMotivation() != null) entity.setMotivation(entity.getMotivation().trim());
            if (entity.getAffiliationName() != null) entity.setAffiliationName(entity.getAffiliationName().trim());
            if (entity.getAffiliationCity() != null) entity.setAffiliationCity(entity.getAffiliationCity().trim());
            if (entity.getAffiliationCountry() != null) entity.setAffiliationCountry(entity.getAffiliationCountry().trim());
            if (entity.getBornCity() != null) entity.setBornCity(entity.getBornCity().trim());
            if (entity.getBornCountry() != null) entity.setBornCountry(entity.getBornCountry().trim());
            if (entity.getBornCountryCode() != null) entity.setBornCountryCode(entity.getBornCountryCode().trim().toUpperCase());

            // Compute age at award if missing and if born and year are available
            if (entity.getAgeAtAward() == null) {
                String born = entity.getBorn();
                String year = entity.getYear();
                if (born != null && !born.isBlank() && year != null && !year.isBlank()) {
                    try {
                        // born expected in yyyy-MM-dd, extract year portion
                        int birthYear = Integer.parseInt(born.substring(0, 4));
                        int awardYear = Integer.parseInt(year.trim());
                        int age = awardYear - birthYear;
                        if (age >= 0) {
                            entity.setAgeAtAward(age);
                        } else {
                            logger.warn("Computed negative age for laureate id={}, born={}, year={}", entity.getId(), born, year);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to compute ageAtAward for laureate id={}, born={}, year={}. error={}", entity.getId(), born, year, ex.getMessage());
                    }
                }
            }

            // Normalize country code: prefer bornCountryCode, fallback to derived uppercase of bornCountry (if short)
            if (entity.getNormalizedCountryCode() == null || entity.getNormalizedCountryCode().isBlank()) {
                if (entity.getBornCountryCode() != null && !entity.getBornCountryCode().isBlank()) {
                    entity.setNormalizedCountryCode(entity.getBornCountryCode().trim().toUpperCase());
                } else if (entity.getBornCountry() != null && !entity.getBornCountry().isBlank()) {
                    String country = entity.getBornCountry().trim();
                    // rudimentary fallback: if country length == 2, use uppercase; otherwise leave null
                    if (country.length() == 2) {
                        entity.setNormalizedCountryCode(country.toUpperCase());
                    } else {
                        // do not invent codes; leave normalized country code null if cannot determine reliably
                        entity.setNormalizedCountryCode(null);
                    }
                }
            }

            // Final indexing preparation steps: ensure key searchable fields exist and are normalized
            if (entity.getCategory() != null) entity.setCategory(entity.getCategory().trim());
            if (entity.getGender() != null) entity.setGender(entity.getGender().trim().toLowerCase());

            // IndexingProcessor does not persist other entities. It adjusts current entity fields for indexing readiness.
            logger.info("IndexingProcessor completed for laureate id={}, validated={}, ageAtAward={}, normalizedCountryCode={}",
                    entity.getId(), entity.getValidated(), entity.getAgeAtAward(), entity.getNormalizedCountryCode());

            // No change to validated state here; the workflow engine will transition the entity to READY after this processor completes.
            return entity;
        } catch (Exception e) {
            logger.error("Error processing laureate indexing logic for id={}. error={}", entity.getId(), e.getMessage(), e);
            // No exception should bubble up; return entity as-is to allow workflow handling
            return entity;
        }
    }
}