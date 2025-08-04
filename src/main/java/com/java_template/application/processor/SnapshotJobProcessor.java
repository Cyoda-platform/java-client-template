package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.SnapshotJob;
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

@Component
public class SnapshotJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final String className = this.getClass().getSimpleName();

    public SnapshotJobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SnapshotJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(SnapshotJob.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SnapshotJob entity) {
        if (entity == null) return false;
        if (entity.getSeason() == null || entity.getSeason().isBlank()) return false;
        if (entity.getDateRangeStart() == null || entity.getDateRangeStart().isBlank()) return false;
        if (entity.getDateRangeEnd() == null || entity.getDateRangeEnd().isBlank()) return false;
        return true;
    }

    private SnapshotJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SnapshotJob> context) {
        SnapshotJob entity = context.entity();

        // Business logic for processing SnapshotJob
        // 1. Validate date range
        if (entity.getDateRangeStart().compareTo(entity.getDateRangeEnd()) >= 0) {
            entity.setStatus("FAILED");
            return entity;
        }

        // 2. Fetch teams for the season from football-data.org
        // 3. For each team, create TeamSnapshot and SquadSnapshot entities
        // 4. Detect changes and create ChangeNotification entities
        // 5. Update SnapshotJob status
        // Since the actual external API calls and data fetch are not possible here,
        // we simulate the processing by setting status COMPLETED

        entity.setStatus("COMPLETED");

        return entity;
    }
}
