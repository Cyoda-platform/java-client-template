package com.java_template.application.processor;

import com.java_template.application.entity.importevent.version_1.ImportEvent;
import com.java_template.application.store.InMemoryDataStore;
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

import java.time.Instant;
import java.util.UUID;

@Component
public class IndexImportEventProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexImportEventProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IndexImportEventProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Indexing ImportEvent for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportEvent.class)
            .validate(this::isValidEntity, "Invalid import event for indexing")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportEvent entity) {
        return entity != null;
    }

    private ImportEvent processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportEvent> context) {
        ImportEvent event = context.entity();
        try {
            // Ensure event has id and timestamp
            if (event.getEventId() == null || event.getEventId().isBlank()) {
                event.setEventId("event-" + UUID.randomUUID());
            }
            if (event.getTimestamp() == null || event.getTimestamp().isBlank()) {
                event.setTimestamp(Instant.now().toString());
            }

            // Record event into the audit list if not already present (idempotent)
            boolean exists = InMemoryDataStore.importEvents.stream()
                .anyMatch(e -> e.getEventId() != null && e.getEventId().equals(event.getEventId()));

            if (!exists) {
                InMemoryDataStore.importEvents.add(event);
                logger.info("Indexed ImportEvent {} status={} job={} itemId={}", event.getEventId(), event.getStatus(), event.getJobTechnicalId(), event.getItemId());
            } else {
                logger.info("ImportEvent {} already indexed, skipping", event.getEventId());
            }

            // Optionally update job/item counters in job store if present
            if (event.getJobTechnicalId() != null) {
                try {
                    var job = InMemoryDataStore.jobsByTechnicalId.get(event.getJobTechnicalId());
                    if (job != null) {
                        int failures = (int) InMemoryDataStore.importEvents.stream()
                            .filter(e -> event.getJobTechnicalId().equals(e.getJobTechnicalId()) && "FAILURE".equalsIgnoreCase(e.getStatus()))
                            .count();
                        job.setFailureCount(failures);
                        InMemoryDataStore.jobsByTechnicalId.put(job.getTechnicalId(), job);
                    }
                } catch (Exception ex) {
                    logger.debug("No job store available to update counters for job {}: {}", event.getJobTechnicalId(), ex.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Error indexing import event {}: {}", event != null ? event.getEventId() : "<null>", e.getMessage(), e);
        }
        return event;
    }
}
