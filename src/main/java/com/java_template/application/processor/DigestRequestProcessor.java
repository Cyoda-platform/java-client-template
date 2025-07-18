package com.java_template.application.processor;

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

import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.service.EntityService;
import com.java_template.application.service.SearchConditionRequest;
import com.java_template.application.service.Condition;
import java.util.concurrent.ExecutionException;

@Component
public class DigestRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DigestRequestProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("DigestRequestProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestRequest.class)
                .validate(DigestRequest::isValid, "Invalid DigestRequest state")
                .map(this::processDigestRequestLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestProcessor".equals(modelSpec.operationName()) &&
                "digestRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequest processDigestRequestLogic(DigestRequest request) {
        try {
            logger.info("Processing DigestRequest event for id {}", request.getId());

            // Check linked DigestJob existence
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", request.getId()));

            CompletableFuture<ArrayNode> jobsFuture = entityService.getItemsByCondition(
                    "DigestJob",
                    Integer.parseInt(Config.ENTITY_VERSION),
                    condition);
            ArrayNode jobsNodes = jobsFuture.get();

            if (jobsNodes == null || jobsNodes.size() == 0) {
                logger.info("No linked DigestJob found for DigestRequest id {}", request.getId());
            } else {
                logger.info("Linked DigestJob found for DigestRequest id {}", request.getId());
            }

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error processing DigestRequest id {}", request.getId(), e);
            Thread.currentThread().interrupt();
        }
        return request;
    }
}