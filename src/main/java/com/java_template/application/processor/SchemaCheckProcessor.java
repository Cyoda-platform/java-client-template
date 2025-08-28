package com.java_template.application.processor;

import com.java_template.application.entity.datasource.version_1.DataSource;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SchemaCheckProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SchemaCheckProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SchemaCheckProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataSource for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(DataSource.class)
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

    private boolean isValidEntity(DataSource entity) {
        return entity != null && entity.isValid();
    }

    private DataSource processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataSource> context) {
        DataSource entity = context.entity();

        // Business logic:
        // - Validate the provided schema string for presence of required columns
        // - Set validationStatus = "VALID" if schema contains required columns, else "INVALID"
        // - Update lastFetchedAt timestamp to now (ISO-8601)
        //
        // Notes:
        // - We only modify the DataSource entity that triggered this processor (it will be persisted by Cyoda workflow)
        // - Do not perform add/update/delete operations on this same entity here

        String schema = entity.getSchema();
        if (schema == null || schema.isBlank()) {
            entity.setValidationStatus("INVALID");
            logger.info("DataSource {}: schema is blank or missing. Marked as INVALID.", entity.getId());
            entity.setLastFetchedAt(Instant.now().toString());
            return entity;
        }

        // Normalize schema representation: remove common JSON array characters and split by common delimiters
        String normalized = schema.replaceAll("[\\[\\]\"]", "");
        String[] parts = normalized.split("[,;|]");
        Set<String> columns = Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Define required columns for the dataset schema check.
        // Based on functional requirements for housing dataset, expect at least: price, area, bedrooms
        List<String> requiredColumns = Arrays.asList("price", "area", "bedrooms");

        boolean hasAllRequired = columns.containsAll(requiredColumns);

        if (hasAllRequired) {
            entity.setValidationStatus("VALID");
            logger.info("DataSource {}: schema contains required columns. Marked as VALID.", entity.getId());
        } else {
            entity.setValidationStatus("INVALID");
            logger.info("DataSource {}: schema missing required columns {}. Marked as INVALID.", entity.getId(), requiredColumns);
        }

        entity.setLastFetchedAt(Instant.now().toString());

        return entity;
    }
}