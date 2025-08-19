package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.errorevent.version_1.ErrorEvent;
import com.java_template.application.entity.lookupjob.version_1.LookupJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class PersistErrorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistErrorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PersistErrorProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistErrorProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(LookupJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(LookupJob entity) {
        return entity != null && entity.isValid();
    }

    private LookupJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<LookupJob> context) {
        LookupJob job = context.entity();
        try {
            // attempt to read fetchResponse to get details if present
            String fetchResponseStr = job.getFetchResponse();
            int code = 0;
            String details = null;
            if (fetchResponseStr != null) {
                JsonNode respNode = objectMapper.readTree(fetchResponseStr);
                code = respNode.path("status").asInt(0);
                details = respNode.path("details").asText(null);
            }

            ErrorEvent err = new ErrorEvent();
            err.setCode(code == 0 ? 503 : code);
            if (code == 404) {
                err.setMessage("User not found");
            } else if (code == 400) {
                err.setMessage("Invalid input");
            } else if (code == 503) {
                err.setMessage("Service unavailable");
            } else {
                err.setMessage("Lookup failed");
            }
            err.setDetails(details);
            err.setOccurredAt(Instant.now().toString());
            err.setRelatedJobId(job.getTechnicalId());

            CompletableFuture<UUID> idFuture = entityService.addItem(ErrorEvent.ENTITY_NAME, String.valueOf(ErrorEvent.ENTITY_VERSION), err);
            UUID technicalId = idFuture.get();
            String techIdStr = technicalId.toString();
            job.setResultRef(techIdStr);
            if (code == 404) {
                job.setOutcome("NOT_FOUND");
            } else if (code == 400) {
                job.setOutcome("INVALID_INPUT");
            } else {
                job.setOutcome("ERROR");
            }
            job.setLifecycleState("COMPLETED");
            job.setCompletedAt(Instant.now().toString());
            logger.info("PersistErrorProcessor: persisted error for job={} errorTechId={} code={}", job.getTechnicalId(), techIdStr, code);
        } catch (Exception e) {
            logger.error("PersistErrorProcessor: error persisting error for job={}: {}", job.getTechnicalId(), e.getMessage());
        }
        return job;
    }
}
