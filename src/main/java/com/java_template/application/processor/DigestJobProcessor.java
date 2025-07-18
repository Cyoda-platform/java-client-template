package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestJob;
import com.java_template.application.entity.DigestContent;
import com.java_template.application.entity.DigestRequest;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class DigestJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public DigestJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestJob.class)
            .validate(DigestJob::isValid, "Invalid DigestJob entity state")
            .map(this::processDigestJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestJobProcessor".equals(modelSpec.operationName()) &&
                "digestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestJob processDigestJobLogic(DigestJob job) {
        try {
            processDigestJob(job);
        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error processing DigestJob id {}", job.getId(), e);
            Thread.currentThread().interrupt();
        }
        return job;
    }

    private void processDigestJob(DigestJob job) throws ExecutionException, InterruptedException {
        logger.info("Processing DigestJob event for id {}", job.getId());

        CompletableFuture<ArrayNode> requestsFuture = entityService.getItems("DigestRequest", Config.ENTITY_VERSION);
        ArrayNode requestsNodes = requestsFuture.get();

        if (requestsNodes == null || requestsNodes.size() == 0) {
            logger.info("No DigestRequests found to associate with DigestJob id {}", job.getId());
        } else {
            logger.info("Found {} DigestRequests, processing them for DigestJob id {}", requestsNodes.size(), job.getId());
            for (int i = 0; i < requestsNodes.size(); i++) {
                ObjectNode reqNode = (ObjectNode) requestsNodes.get(i);
                DigestRequest req = JsonUtil.convert(reqNode, DigestRequest.class);

                DigestContent content = new DigestContent();
                content.setId(null);
                content.setTechnicalId(UUID.randomUUID());
                content.setRequestId(req.getId());
                content.setDigestJobId(job.getId());
                content.setContent("Generated content for request " + req.getId());

                CompletableFuture<UUID> addedContentIdFuture = entityService.addItem(
                        "DigestContent",
                        Config.ENTITY_VERSION,
                        content);
                addedContentIdFuture.get();

                processDigestContent(content);
            }
        }

        logger.info("DigestJob event processing completed for id {}", job.getId());
    }

    private void processDigestContent(DigestContent content) {
        logger.info("Processing DigestContent event for id {}", content.getId());

        if (content.getContent() == null || content.getContent().isBlank()) {
            logger.warn("DigestContent id {} has empty content field", content.getId());
        } else {
            logger.info("DigestContent id {} content length: {}", content.getId(), content.getContent().length());
        }
    }

    // Hypothetical injected entityService - assuming available in context for prototype logic
    private final EntityService entityService = EntityService.getInstance();
}