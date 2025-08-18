package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.application.entity.retrievaljob.version_1.RetrievalJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class ProcessRetrievalJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessRetrievalJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProcessRetrievalJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing RetrievalJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(RetrievalJob.class)
            .validate(this::isValidEntity, "Invalid retrieval job state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(RetrievalJob entity) {
        return entity != null && entity.isValid();
    }

    private RetrievalJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<RetrievalJob> context) {
        RetrievalJob job = context.entity();
        try {
            job.setStatus("LOOKUP");
            long itemId = job.getItemId();

            SearchConditionRequest cond = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", String.valueOf(itemId))
            );

            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                HNItem.ENTITY_NAME,
                String.valueOf(HNItem.ENTITY_VERSION),
                cond,
                true
            );

            com.fasterxml.jackson.databind.node.ArrayNode found = itemsFuture.get();
            if (found != null && found.size() > 0) {
                ObjectNode existing = (ObjectNode) found.get(0);
                ObjectNode result = mapper.createObjectNode();
                result.set("rawJson", existing.get("rawJson"));
                if (existing.has("metadata")) result.set("metadata", existing.get("metadata"));
                job.setResult(mapper.convertValue(result, Object.class));
                job.setStatus("FOUND");
            } else {
                job.setStatus("NOT_FOUND");
            }
        } catch (Exception e) {
            logger.error("Error during retrieval for job {}: {}", context.requestId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
        }
        return job;
    }
}
