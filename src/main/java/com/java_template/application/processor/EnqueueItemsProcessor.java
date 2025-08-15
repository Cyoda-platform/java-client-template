package com.java_template.application.processor;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class EnqueueItemsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnqueueItemsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EnqueueItemsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EnqueueItems for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(IngestJob.class)
            .validate(this::isValidEntity, "Invalid ingest job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestJob entity) {
        return entity != null && entity.getCreatedItemTechnicalIds() != null && !entity.getCreatedItemTechnicalIds().isEmpty();
    }

    private IngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestJob> context) {
        IngestJob job = context.entity();
        // This processor ensures items already created by SplitPayloadProcessor are marked ENQUEUED and triggers downstream processing
        try {
            List<String> tids = new ArrayList<>(job.getCreatedItemTechnicalIds());
            for (String tid : tids) {
                HNItem item = HNItemRepository.getInstance().findByTechnicalId(tid);
                if (item != null) {
                    item.setStatus("RECEIVED");
                    item.setUpdatedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    HNItemRepository.getInstance().save(item);
                    logger.info("Enqueued HNItem {} from IngestJob {}", tid, job.getTechnicalId());
                }
            }
            job.setStatus("ITEMS_ENQUEUED");
            job.setUpdatedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            return job;
        } catch (Exception e) {
            logger.error("Error enqueuing items for IngestJob {}: {}", job == null ? "<null>" : job.getTechnicalId(), e.getMessage(), e);
            if (job != null) {
                job.setStatus("FAILED");
                job.setUpdatedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            return job;
        }
    }
}
