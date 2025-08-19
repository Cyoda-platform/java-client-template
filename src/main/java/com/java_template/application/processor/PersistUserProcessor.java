package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.lookupjob.version_1.LookupJob;
import com.java_template.application.entity.user.version_1.User;
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
public class PersistUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PersistUserProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PersistUserProcessor for request: {}", request.getId());

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
            // fetchResponse expected to be JSON string stored in job
            String fetchResponseStr = job.getFetchResponse();
            if (fetchResponseStr == null) {
                logger.warn("PersistUserProcessor: fetchResponse missing for job={}", job.getTechnicalId());
                return job;
            }
            JsonNode respNode = objectMapper.readTree(fetchResponseStr);
            int status = respNode.path("status").asInt(0);
            JsonNode body = respNode.path("body");
            if (status == 200 && body != null && !body.isMissingNode()) {
                User user = new User();
                user.setId(body.path("id").asInt());
                user.setEmail(body.path("email").asText(null));
                user.setFirst_name(body.path("first_name").asText(null));
                user.setLast_name(body.path("last_name").asText(null));
                user.setAvatar(body.path("avatar").asText(null));
                user.setRetrievedAt(Instant.now().toString());

                CompletableFuture<UUID> idFuture = entityService.addItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), user);
                UUID technicalId = idFuture.get();
                String techIdStr = technicalId.toString();
                job.setResultRef(techIdStr);
                job.setOutcome("SUCCESS");
                job.setLifecycleState("COMPLETED");
                job.setCompletedAt(Instant.now().toString());
                logger.info("PersistUserProcessor: persisted user for job={} userTechId={}", job.getTechnicalId(), techIdStr);
            } else {
                logger.warn("PersistUserProcessor: unexpected status={} for job={}", status, job.getTechnicalId());
            }
        } catch (Exception e) {
            logger.error("PersistUserProcessor: error persisting user for job={}: {}", job.getTechnicalId(), e.getMessage());
        }
        return job;
    }
}
