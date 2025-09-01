package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class ScheduleSendProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleSendProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ScheduleSendProcessor(SerializerFactory serializerFactory,
                                 EntityService entityService,
                                 ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
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

    private boolean isValidEntity(CatFact entity) {
        return entity != null && entity.isValid();
    }

    private CatFact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFact> context) {
        CatFact entity = context.entity();

        // Business Rule:
        // Only schedule CatFacts that have passed validation (validationStatus == "VALID")
        if (entity.getValidationStatus() == null || !entity.getValidationStatus().equalsIgnoreCase("VALID")) {
            logger.info("CatFact {} is not VALID (status={}); skipping scheduling.", entity.getTechnicalId(), entity.getValidationStatus());
            return entity;
        }

        try {
            // Try to find a WeeklySendJob currently in RUNNING state to attach this CatFact to.
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.status", "EQUALS", "RUNNING")
            );

            List<DataPayload> jobs = entityService.getItemsByCondition(
                WeeklySendJob.ENTITY_NAME,
                WeeklySendJob.ENTITY_VERSION,
                condition,
                true
            ).get();

            if (jobs != null && !jobs.isEmpty()) {
                // Attach to the first running job found
                DataPayload payload = jobs.get(0);
                String jobTechnicalId = payload.getMeta().get("entityId").asText();
                WeeklySendJob job = objectMapper.treeToValue(payload.getData(), WeeklySendJob.class);

                // Set reference to this CatFact on the job
                job.setCatFactTechnicalId(entity.getTechnicalId());

                // Ensure runAt is set when scheduling if it's missing
                if (job.getRunAt() == null || job.getRunAt().isBlank()) {
                    job.setRunAt(OffsetDateTime.now().toString());
                }

                // Persist the updated job (do not modify other fields)
                try {
                    entityService.updateItem(UUID.fromString(jobTechnicalId), job).get();
                    logger.info("Attached CatFact {} to WeeklySendJob {}", entity.getTechnicalId(), jobTechnicalId);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to update WeeklySendJob {}: {}", jobTechnicalId, e.getMessage(), e);
                    // Mark job failure message and rethrow to allow upstream error handling if needed
                    job.setErrorMessage("Failed to attach CatFact: " + e.getMessage());
                    try {
                        entityService.updateItem(UUID.fromString(jobTechnicalId), job).get();
                    } catch (Exception ex) {
                        logger.error("Failed to persist errorMessage for WeeklySendJob {}: {}", jobTechnicalId, ex.getMessage(), ex);
                    }
                }
            } else {
                logger.info("No RUNNING WeeklySendJob found to attach CatFact {}", entity.getTechnicalId());
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while searching for WeeklySendJob to attach CatFact {}: {}", entity.getTechnicalId(), e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error in ScheduleSendProcessor for CatFact {}: {}", entity.getTechnicalId(), e.getMessage(), e);
        }

        // Do not modify the CatFact entity state here except business-required fields.
        // This processor's responsibility is to attach the CatFact to a WeeklySendJob.
        return entity;
    }
}