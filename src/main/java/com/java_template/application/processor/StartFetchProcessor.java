package com.java_template.application.processor;

import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StartFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private static final Pattern LIMIT_PATTERN = Pattern.compile("limit=(\\d+)");

    public StartFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetEnrichmentJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetEnrichmentJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetEnrichmentJob entity) {
        return entity != null && entity.isValid();
    }

    private PetEnrichmentJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetEnrichmentJob> context) {
        PetEnrichmentJob entity = context.entity();

        // Ensure errors list exists and is cleared for a fresh run
        if (entity.getErrors() == null) {
            entity.setErrors(new ArrayList<>());
        } else {
            entity.getErrors().clear();
        }

        try {
            // Move job to in-progress before attempting fetch operations
            entity.setStatus("IN_PROGRESS");

            String source = entity.getPetSource();
            if (source == null || source.isBlank()) {
                entity.getErrors().add("petSource is blank");
                entity.setStatus("FAILED");
                logger.warn("PetEnrichmentJob {} failed: petSource is blank", entity.getJobId());
                return entity;
            }

            // Try to extract a limit parameter from petSource query string if present
            Integer fetchedCount = null;
            Matcher m = LIMIT_PATTERN.matcher(source);
            if (m.find()) {
                try {
                    int limit = Integer.parseInt(m.group(1));
                    if (limit < 0) {
                        entity.getErrors().add("Invalid limit parameter in petSource: " + m.group(1));
                        entity.setStatus("FAILED");
                        logger.warn("PetEnrichmentJob {} invalid limit: {}", entity.getJobId(), m.group(1));
                        return entity;
                    }
                    // Use parsed limit as an initial expected fetched count (placeholder)
                    fetchedCount = limit;
                } catch (NumberFormatException nfex) {
                    entity.getErrors().add("Invalid numeric limit in petSource: " + m.group(1));
                    entity.setStatus("FAILED");
                    logger.warn("PetEnrichmentJob {} has non-numeric limit '{}'", entity.getJobId(), m.group(1));
                    return entity;
                }
            }

            // If no explicit limit is found, default to 0 (will be updated when actual fetch occurs)
            if (fetchedCount == null) {
                fetchedCount = 0;
            }

            // Set the fetchedCount on the job entity. Actual fetching and creation of Pet entities
            // may be performed by another component; here we only prepare the job state.
            entity.setFetchedCount(fetchedCount);

            // If we've reached here without errors, keep status IN_PROGRESS (the workflow will continue)
            logger.info("Started fetch for PetEnrichmentJob {}: status={}, fetchedCount={}, errors={}",
                entity.getJobId(), entity.getStatus(), entity.getFetchedCount(), entity.getErrors());

        } catch (Exception ex) {
            logger.error("Error while starting fetch for job {}: {}", entity.getJobId(), ex.getMessage(), ex);
            entity.getErrors().add("Fetch start error: " + ex.getMessage());
            entity.setStatus("FAILED");
        }

        return entity;
    }
}