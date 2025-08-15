package com.java_template.application.processor;

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
import java.util.concurrent.CompletableFuture;

@Component
public class EnrichImportTimestampProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichImportTimestampProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EnrichImportTimestampProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HNItem entity) {
        return entity != null && "VALIDATED".equalsIgnoreCase(entity.getStatus());
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();
        try {
            String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            entity.setImportTimestamp(now);
            entity.setUpdatedAt(now);
            entity.setStatus("ENRICHED");

            // Persist change via entityService to ensure stored state is propagated outside the engine
            try {
                CompletableFuture<java.util.UUID> fut = entityService.addItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    entity
                );
                java.util.UUID id = fut.join();
                if (id != null) {
                    entity.setTechnicalId(id.toString());
                }
            } catch (Exception e) {
                logger.warn("Failed to persist HNItem via EntityService during enrichment: {}", e.getMessage());
            }

            logger.info("Enriched HNItem {} with importTimestamp {}", entity.getTechnicalId(), now);
            return entity;
        } catch (Exception e) {
            logger.error("Error enriching HNItem {}: {}", entity == null ? "<null>" : entity.getTechnicalId(), e.getMessage(), e);
            if (entity != null) {
                entity.setStatus("INVALID");
                entity.setErrorMessage("Enrichment error: " + e.getMessage());
            }
            return entity;
        }
    }
}
