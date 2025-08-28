package com.java_template.application.processor;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
        logger.info("Processing Laureate field formats for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ObjectNode.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity as ObjectNode: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ObjectNode node) {
        if (node == null) return false;
        // Required fields according to Laureate entity: id, firstname, surname, category, year
        JsonNode id = node.get("id");
        JsonNode firstname = node.get("firstname");
        JsonNode surname = node.get("surname");
        JsonNode category = node.get("category");
        JsonNode year = node.get("year");

        if (id == null || id.asText().isBlank()) return false;
        if (firstname == null || firstname.asText().isBlank()) return false;
        if (surname == null || surname.asText().isBlank()) return false;
        if (category == null || category.asText().isBlank()) return false;
        if (year == null || year.asText().isBlank()) return false;

        return true;
    }

    private ObjectNode processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ObjectNode> context) {
        ObjectNode node = context.entity();
        String technicalId = node.path("id").asText(null);

        boolean allFormatsOk = true;

        // Validate year: must be a 4-digit integer within reasonable range
        String year = node.path("year").asText(null);
        if (year == null || year.isBlank()) {
            logger.warn("Laureate {} has missing year", technicalId);
            allFormatsOk = false;
        } else {
            try {
                int y = Integer.parseInt(year);
                if (year.length() != 4 || y < 1800 || y > 2100) {
                    logger.warn("Laureate {} has invalid year value: {}", technicalId, year);
                    allFormatsOk = false;
                }
            } catch (NumberFormatException ex) {
                logger.warn("Laureate {} year is not a number: {}", technicalId, year);
                allFormatsOk = false;
            }
        }

        // Validate born date format (yyyy-MM-dd) if present
        JsonNode bornNode = node.get("born");
        if (bornNode != null && !bornNode.isNull() && !bornNode.asText().isBlank()) {
            String born = bornNode.asText();
            try {
                LocalDate.parse(born);
            } catch (DateTimeParseException ex) {
                logger.warn("Laureate {} has invalid born date format: {}", technicalId, born);
                allFormatsOk = false;
            }
        }

        // Validate died date format (yyyy-MM-dd) if present (died may be null)
        JsonNode diedNode = node.get("died");
        if (diedNode != null && !diedNode.isNull() && !diedNode.asText().isBlank()) {
            String died = diedNode.asText();
            try {
                LocalDate.parse(died);
            } catch (DateTimeParseException ex) {
                logger.warn("Laureate {} has invalid died date format: {}", technicalId, died);
                allFormatsOk = false;
            }
        }

        // Validate born country code: if present must be two alphabetic characters
        JsonNode bornCountryCodeNode = node.get("bornCountryCode");
        if (bornCountryCodeNode != null && !bornCountryCodeNode.isNull() && !bornCountryCodeNode.asText().isBlank()) {
            String bornCountryCode = bornCountryCodeNode.asText().trim();
            if (bornCountryCode.length() == 2 && bornCountryCode.matches("[A-Za-z]{2}")) {
                String normalized = bornCountryCode.toUpperCase();
                node.set("normalizedCountryCode", TextNode.valueOf(normalized));
            } else {
                logger.warn("Laureate {} has invalid bornCountryCode: {}", technicalId, bornCountryCode);
                allFormatsOk = false;
            }
        } else {
            // If no bornCountryCode but bornCountry present, we can set normalizedCountryCode later in enrichment processors.
            // Do nothing here.
        }

        // Validate gender if present: accept common values
        JsonNode genderNode = node.get("gender");
        if (genderNode != null && !genderNode.isNull() && !genderNode.asText().isBlank()) {
            String gender = genderNode.asText().trim().toLowerCase();
            if (gender.equals("m") || gender.equals("male")) {
                node.set("gender", TextNode.valueOf("male"));
            } else if (gender.equals("f") || gender.equals("female")) {
                node.set("gender", TextNode.valueOf("female"));
            } else if (gender.equals("other") || gender.equals("unknown")) {
                node.set("gender", TextNode.valueOf(gender));
            } else {
                logger.warn("Laureate {} has unexpected gender value: {}", technicalId, genderNode.asText());
                // Not strictly fatal but mark as format issue
                allFormatsOk = false;
            }
        }

        // Set validation flag based on format checks
        if (allFormatsOk) {
            node.set("validated", TextNode.valueOf("VALIDATED"));
            logger.info("Laureate {} field formats validated", technicalId);
        } else {
            node.set("validated", TextNode.valueOf("INVALID"));
            logger.info("Laureate {} field formats invalid", technicalId);
        }

        return node;
    }
}