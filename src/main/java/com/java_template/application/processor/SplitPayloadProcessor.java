package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class SplitPayloadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SplitPayloadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final EntityService entityService;

    public SplitPayloadProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SplitPayload for request: {}", request.getId());

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
        return entity != null && entity.getPayload() != null;
    }

    private IngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestJob> context) {
        IngestJob job = context.entity();
        try {
            job.setStatus("SPLITTING");
            job.setUpdatedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            String payload = job.getPayload();
            JsonNode root = mapper.readTree(payload);
            if (!root.isArray()) {
                logger.warn("IngestJob payload is not an array; wrapping single object into array");
                List<String> technicalIds = new ArrayList<>();
                String tid = UUID.randomUUID().toString();
                HNItem item = new HNItem();
                item.setTechnicalId(tid);
                item.setRawJson(payload);
                item.setStatus("RECEIVED");
                String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                item.setCreatedAt(now);
                item.setUpdatedAt(now);

                // persist the hn item using entityService
                try {
                    CompletableFuture<java.util.UUID> fut = entityService.addItem(
                        HNItem.ENTITY_NAME,
                        String.valueOf(HNItem.ENTITY_VERSION),
                        item
                    );
                    java.util.UUID id = fut.join();
                    if (id != null) item.setTechnicalId(id.toString());
                } catch (Exception ex) {
                    logger.warn("Failed to persist HNItem via EntityService during split: {}", ex.getMessage());
                }

                HNItemRepository.getInstance().save(item);
                technicalIds.add(tid);
                job.setCreatedItemTechnicalIds(technicalIds);
                job.setStatus("ITEMS_ENQUEUED");
                job.setUpdatedAt(now);
                logger.info("Enqueued single HNItem {} from ingest job {}", tid, job.getTechnicalId());
                return job;
            }

            Iterator<JsonNode> it = root.elements();
            List<String> technicalIds = new ArrayList<>();
            while (it.hasNext()) {
                JsonNode node = it.next();
                String raw = mapper.writeValueAsString(node);
                String tid = UUID.randomUUID().toString();
                HNItem item = new HNItem();
                item.setTechnicalId(tid);
                item.setRawJson(raw);
                if (node.hasNonNull("id")) {
                    item.setId(node.get("id").asLong());
                }
                item.setStatus("RECEIVED");
                String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                item.setCreatedAt(now);
                item.setUpdatedAt(now);

                try {
                    CompletableFuture<java.util.UUID> fut = entityService.addItem(
                        HNItem.ENTITY_NAME,
                        String.valueOf(HNItem.ENTITY_VERSION),
                        item
                    );
                    java.util.UUID id = fut.join();
                    if (id != null) item.setTechnicalId(id.toString());
                } catch (Exception ex) {
                    logger.warn("Failed to persist HNItem via EntityService during split: {}", ex.getMessage());
                }

                HNItemRepository.getInstance().save(item);
                technicalIds.add(tid);
                logger.info("Created HNItem {} from ingest job {}", tid, job.getTechnicalId());
            }

            job.setCreatedItemTechnicalIds(technicalIds);
            job.setStatus("ITEMS_ENQUEUED");
            job.setUpdatedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            logger.info("Split payload and enqueued {} items for job {}", technicalIds.size(), job.getTechnicalId());
            return job;

        } catch (Exception e) {
            logger.error("Error splitting payload for IngestJob {}: {}", job == null ? "<null>" : job.getTechnicalId(), e.getMessage(), e);
            if (job != null) {
                job.setStatus("FAILED");
                job.setUpdatedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            return job;
        }
    }
}
