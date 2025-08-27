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

import java.util.HashMap;
import java.util.Map;

@Component
public class EnrichLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichLaureateProcessor(SerializerFactory serializerFactory) {
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

        // 1. Compute ageAtAward if possible
        try {
            String born = entity.getBorn();
            String awardYear = entity.getAwardYear();
            if (born != null && !born.isBlank() && awardYear != null && !awardYear.isBlank()) {
                // extract leading 4-digit year from born (e.g., "1930-09-12" or "1930")
                String bornYearStr = null;
                born = born.trim();
                if (born.length() >= 4) {
                    char c0 = born.charAt(0);
                    char c1 = born.charAt(1);
                    char c2 = born.charAt(2);
                    char c3 = born.charAt(3);
                    if (Character.isDigit(c0) && Character.isDigit(c1) && Character.isDigit(c2) && Character.isDigit(c3)) {
                        bornYearStr = born.substring(0, 4);
                    }
                }
                if (bornYearStr != null) {
                    String awardYearTrim = awardYear.trim();
                    // awardYear may contain non-digit characters; extract leading digits
                    String awardYearDigits = awardYearTrim.length() >= 4 && Character.isDigit(awardYearTrim.charAt(0))
                            ? awardYearTrim.replaceAll("^([^0-9]*)([0-9]{4}).*$", "$2")
                            : awardYearTrim;
                    int by = Integer.parseInt(bornYearStr);
                    int ay = Integer.parseInt(awardYearDigits);
                    int age = ay - by;
                    if (age >= 0) {
                        entity.setAgeAtAward(age);
                    } else {
                        // negative age, consider unresolved; clear value and add a validation note
                        entity.setAgeAtAward(null);
                        entity.getValidationErrors().add("Computed negative ageAtAward from born and awardYear");
                        logger.warn("Negative computed ageAtAward for laureateId={}, born={}, awardYear={}", entity.getLaureateId(), born, awardYear);
                    }
                } else {
                    entity.getValidationErrors().add("Unable to parse birth year from born field for age computation");
                    logger.debug("Unable to extract birth year from born='{}' for laureateId={}", born, entity.getLaureateId());
                }
            }
        } catch (Exception ex) {
            // Protect processing from unexpected parsing issues
            entity.setAgeAtAward(null);
            entity.getValidationErrors().add("Error computing ageAtAward: " + ex.getMessage());
            logger.error("Error computing ageAtAward for laureateId={}: {}", entity.getLaureateId(), ex.getMessage());
        }

        // 2. Normalize borncountrycode when missing using a small heuristic map
        String code = entity.getBorncountrycode();
        if (code == null || code.isBlank()) {
            String country = entity.getBorncountry();
            if (country != null && !country.isBlank()) {
                String normalized = country.trim();
                Map<String, String> common = new HashMap<>();
                common.put("japan", "JP");
                common.put("united states", "US");
                common.put("united states of america", "US");
                common.put("usa", "US");
                common.put("sweden", "SE");
                common.put("united kingdom", "GB");
                common.put("great britain", "GB");
                common.put("uk", "GB");
                common.put("germany", "DE");
                common.put("france", "FR");
                common.put("norway", "NO");
                common.put("denmark", "DK");
                common.put("netherlands", "NL");
                common.put("canada", "CA");
                common.put("china", "CN");
                String key = normalized.toLowerCase();
                if (common.containsKey(key)) {
                    entity.setBorncountrycode(common.get(key));
                } else {
                    // If not in mapping, attempt to use ISO-like uppercasing of two-letter names (best-effort)
                    if (normalized.length() == 2 && Character.isLetter(normalized.charAt(0)) && Character.isLetter(normalized.charAt(1))) {
                        entity.setBorncountrycode(normalized.toUpperCase());
                    } else {
                        // leave null but record note
                        entity.getValidationErrors().add("borncountrycode not inferred for borncountry='" + country + "'");
                        logger.debug("Could not infer country code for borncountry='{}' laureateId={}", country, entity.getLaureateId());
                    }
                }
            }
        }

        // 3. Trim string fields to normalize whitespace
        if (entity.getFirstname() != null) entity.setFirstname(entity.getFirstname().trim());
        if (entity.getSurname() != null) entity.setSurname(entity.getSurname().trim());
        if (entity.getMotivation() != null) entity.setMotivation(entity.getMotivation().trim());
        if (entity.getAffiliationName() != null) entity.setAffiliationName(entity.getAffiliationName().trim());
        if (entity.getAffiliationCity() != null) entity.setAffiliationCity(entity.getAffiliationCity().trim());
        if (entity.getAffiliationCountry() != null) entity.setAffiliationCountry(entity.getAffiliationCountry().trim());
        if (entity.getBorncountry() != null) entity.setBorncountry(entity.getBorncountry().trim());
        if (entity.getBorncity() != null) entity.setBorncity(entity.getBorncity().trim());

        // 4. Mark processing status as ENRICHED
        entity.setProcessingStatus("ENRICHED");

        logger.info("Enriched laureate laureateId={} awardYear={} ageAtAward={} countryCode={}",
                entity.getLaureateId(), entity.getAwardYear(), entity.getAgeAtAward(), entity.getBorncountrycode());

        return entity;
    }
}