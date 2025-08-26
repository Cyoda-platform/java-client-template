package com.java_template.application.processor;

import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Component
public class FinalizeJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FinalizeJobProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Lightweight validation that ensures the processor only runs when there is something to inspect.
     * Do NOT rely on ImportJob.isValid() here because processedItemId is optional during workflow progress.
     */
    private boolean isValidEntity(ImportJob entity) {
        if (entity == null) return false;
        // valid if we have either the item payload or a reference to a processed item or at least a status
        if (entity.getItemJson() != null) return true;
        if (entity.getProcessedItemId() != null) return true;
        return entity.getStatus() != null && !entity.getStatus().isBlank();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        String jobRef = Objects.toString(job != null ? job.getJobId() : null, "unknown");
        String currentStatus = job != null ? job.getStatus() : null;
        try {
            logger.info("Finalizing ImportJob jobId={} technical entity state status={}", jobRef, currentStatus);

            // Try to determine HN item id to inspect its processing result.
            Long hnId = job.getProcessedItemId();

            // If processedItemId is not present, attempt to extract from itemJson payload
            if (hnId == null && job.getItemJson() != null) {
                try {
                    ObjectNode itemNode = objectMapper.convertValue(job.getItemJson(), ObjectNode.class);
                    if (itemNode != null && itemNode.has("id") && !itemNode.get("id").isNull()) {
                        hnId = itemNode.get("id").asLong();
                        job.setProcessedItemId(hnId);
                        logger.debug("Extracted hnId={} from itemJson for jobId={}", hnId, jobRef);
                    }
                } catch (IllegalArgumentException e) {
                    logger.debug("Unable to convert itemJson to ObjectNode to extract id for jobId={}: {}", jobRef, e.getMessage());
                }
            }

            if (hnId == null) {
                // Nothing to finalize against; mark as FAILED to indicate we couldn't resolve the item
                logger.warn("ImportJob {} has no processedItemId and itemJson did not contain an id. Marking job FAILED.", jobRef);
                job.setStatus("FAILED");
                return job;
            }

            // Query HNItem entity by its id (searching by field $.id)
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", String.valueOf(hnId))
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                HNItem.ENTITY_NAME,
                String.valueOf(HNItem.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode items = null;
            try {
                items = itemsFuture.join();
            } catch (Exception e) {
                logger.error("Error while querying HNItem for id={} for ImportJob {}: {}", hnId, jobRef, e.getMessage(), e);
                // treat as transient failure - leave job status unchanged so monitor can retry
                return job;
            }

            if (items == null || items.size() == 0) {
                logger.warn("HNItem with id {} not found for ImportJob {}. Marking job FAILED.", hnId, jobRef);
                job.setStatus("FAILED");
                return job;
            }

            // Use the first matched HNItem
            ObjectNode hnItemNode = (ObjectNode) items.get(0);
            String hnStatus = hnItemNode.has("status") && !hnItemNode.get("status").isNull() ? hnItemNode.get("status").asText() : null;

            if ("STORED".equalsIgnoreCase(hnStatus)) {
                logger.info("HNItem {} is STORED. Marking ImportJob {} as COMPLETED.", hnId, jobRef);
                job.setStatus("COMPLETED");
                // ensure processedItemId is set from the HNItem payload if missing
                if (job.getProcessedItemId() == null && hnItemNode.has("id") && !hnItemNode.get("id").isNull()) {
                    job.setProcessedItemId(hnItemNode.get("id").asLong());
                }
            } else if ("FAILED".equalsIgnoreCase(hnStatus)) {
                logger.info("HNItem {} is FAILED. Marking ImportJob {} as FAILED.", hnId, jobRef);
                job.setStatus("FAILED");
            } else {
                // HNItem is not yet in a terminal state; leave job as-is (monitoring/waiting should continue)
                logger.info("HNItem {} is in state '{}'. Leaving ImportJob {} status unchanged (was={})", hnId, hnStatus, jobRef, currentStatus);
            }

        } catch (Exception ex) {
            logger.error("Error finalizing ImportJob {}: {}", jobRef, ex.getMessage(), ex);
            // On unexpected errors, mark job as FAILED
            job.setStatus("FAILED");
        }

        return job;
    }
}