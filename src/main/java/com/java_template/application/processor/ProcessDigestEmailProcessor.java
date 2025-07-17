package com.java_template.application.processor;

import com.java_template.application.entity.DigestEmail;
import com.java_template.common.config.Config;
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
import java.util.concurrent.CompletableFuture;

@Component
public class ProcessDigestEmailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public ProcessDigestEmailProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("ProcessDigestEmailProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestEmail for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestEmail.class)
                .validate(this::isValidEntity, "Invalid DigestEmail entity")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ProcessDigestEmailProcessor".equals(modelSpec.operationName()) &&
                "digestEmail".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(DigestEmail entity) {
        return entity.getId() != null && !entity.getId().isEmpty()
                && entity.getSentStatus() != null && !entity.getSentStatus().isEmpty();
    }

    private DigestEmail processEntityLogic(DigestEmail digestEmail) {
        logger.info("Processing DigestEmail event, id={}", digestEmail.getId());

        digestEmail.setSentStatus("SENT");
        digestEmail.setSentAt(Instant.now());

        try {
            CompletableFuture<UUID> updateFuture = entityService.updateItem(
                    "DigestEmail",
                    Config.ENTITY_VERSION,
                    UUID.fromString(digestEmail.getId()),
                    digestEmail
            );
            updateFuture.get();
            logger.info("DigestEmail id={} marked as SENT", digestEmail.getId());
        } catch (Exception e) {
            logger.error("Failed to update DigestEmail sent status: {}", e.toString());
        }

        return digestEmail;
    }
}
