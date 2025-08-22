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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StartFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

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

        // Ensure errors list exists
        if (entity.getErrors() == null) {
            entity.setErrors(new ArrayList<>());
        } else {
            entity.getErrors().clear();
        }

        // Default fetched count
        int fetchedCount = 0;

        try {
            // Set job status to IN_PROGRESS as we begin fetching
            entity.setStatus("IN_PROGRESS");

            String source = entity.getPetSource();
            if (source != null && !source.isBlank()) {
                // If a limit parameter is present (e.g., "limit=50"), use it to simulate fetched count.
                Pattern p = Pattern.compile("limit=(\\d+)");
                Matcher m = p.matcher(source);
                if (m.find()) {
                    try {
                        fetchedCount = Integer.parseInt(m.group(1));
                    } catch (NumberFormatException nfe) {
                        // fallback to 0 and record error
                        fetchedCount = 0;
                        entity.getErrors().add("Invalid limit parameter in petSource: " + m.group(1));
                        entity.setStatus("FAILED");
                    }
                } else {
                    // No explicit limit; assume zero fetched until a real fetch is implemented.
                    fetchedCount = 0;
                }
            } else {
                entity.getErrors().add("petSource is blank");
                entity.setStatus("FAILED");
            }
        } catch (Exception ex) {
            logger.error("Error while starting fetch for job {}: {}", entity.getJobId(), ex.getMessage(), ex);
            entity.getErrors().add("Fetch start error: " + ex.getMessage());
            entity.setStatus("FAILED");
        }

        // Ensure fetchedCount is non-negative and set on entity
        if (fetchedCount < 0) {
            fetchedCount = 0;
        }
        entity.setFetchedCount(fetchedCount);

        logger.info("StartFetchProcessor updated job {}: status={}, fetchedCount={}, errors={}",
            entity.getJobId(), entity.getStatus(), entity.getFetchedCount(), entity.getErrors());

        // NOTE: actual external fetch and event publication (RawPetsFetched) should be implemented
        // by integrating a Petstore client and event publisher. This processor prepares the job state.
        return entity;
    }
}