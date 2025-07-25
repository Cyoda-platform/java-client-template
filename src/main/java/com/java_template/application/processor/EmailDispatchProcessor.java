package com.java_template.application.processor;

import com.java_template.application.entity.EmailDispatch;
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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

@Component
public class EmailDispatchProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public EmailDispatchProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("EmailDispatchProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailDispatch for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(EmailDispatch.class)
                .validate(EmailDispatch::isValid, "Invalid EmailDispatch entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailDispatchProcessor".equals(modelSpec.operationName()) &&
                "emailDispatch".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EmailDispatch processEntityLogic(EmailDispatch entity) {
        // The processEntityLogic method requires the technicalId for update operations.
        // This is not directly provided here, so simulate by assuming the entity has a method to get it or pass it if available.
        // Since we don't have direct access to technicalId here, the logic is implemented in process() method instead.
        // However, the prototype logic requires technicalId, so we implement the logic here by fetching it from serializer context.

        // Instead, to comply with the prototype logic, we will move the processing logic to the process method
        // and keep this method as a placeholder for entity transformation if needed.
        return entity;
    }

    // Actual processing moved to process method due to need of technicalId
    private EntityProcessorCalculationResponse processWithLogic(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        String technicalId = request.getId();

        EmailDispatch entity = serializer.extractEntity(request, EmailDispatch.class);

        logger.info("Processing EmailDispatch with technicalId: {}", technicalId);
        try {
            entity.setStatus("PROCESSING");
            entityService.updateItem("EmailDispatch", Config.ENTITY_VERSION, UUID.fromString(technicalId), entity).get();

            // Retrieve associated DigestRequest
            String digestRequestId = entity.getDigestRequestId();
            UUID digestRequestTechnicalId = UUID.fromString(digestRequestId);
            CompletableFuture<ObjectNode> digestRequestFuture = entityService.getItem("DigestRequest", Config.ENTITY_VERSION, digestRequestTechnicalId);
            ObjectNode digestRequestNode = digestRequestFuture.get();
            if (digestRequestNode == null) {
                logger.error("Associated DigestRequest not found for EmailDispatch technicalId: {}", technicalId);
                entity.setStatus("FAILED");
                entityService.updateItem("EmailDispatch", Config.ENTITY_VERSION, UUID.fromString(technicalId), entity).get();
                return serializer.responseBuilder(request).failure("Associated DigestRequest not found").build();
            }

            DigestRequest digestRequest = serializer.entityToJsonNode(entity).traverse().getCodec().treeToValue(digestRequestNode, DigestRequest.class);

            // Retrieve associated successful DigestData by condition: digestRequestId = digestRequestId AND status = "SUCCESS"
            Condition cond1 = Condition.of("$.digestRequestId", "EQUALS", digestRequestId);
            Condition cond2 = Condition.of("$.status", "EQUALS", "SUCCESS");
            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", cond1, cond2);

            CompletableFuture<ArrayNode> digestDataArrayFuture = entityService.getItemsByCondition("DigestData", Config.ENTITY_VERSION, conditionRequest, true);
            ArrayNode digestDataArray = digestDataArrayFuture.get();

            if (digestDataArray == null || digestDataArray.size() == 0) {
                logger.error("Associated successful DigestData not found for EmailDispatch technicalId: {}", technicalId);
                entity.setStatus("FAILED");
                entityService.updateItem("EmailDispatch", Config.ENTITY_VERSION, UUID.fromString(technicalId), entity).get();
                return serializer.responseBuilder(request).failure("Associated DigestData not found").build();
            }

            ObjectNode digestDataNode = (ObjectNode) digestDataArray.get(0);
            DigestData digestData = serializer.entityToJsonNode(entity).traverse().getCodec().treeToValue(digestDataNode, DigestData.class);

            String emailContent = "Digest for request ID: " + entity.getDigestRequestId() + "\n\nData:\n" + digestData.getApiData();
            entity.setEmailContent(emailContent);

            logger.info("Sending email to {} with digest content length {}", digestRequest.getEmail(), emailContent.length());
            // Simulate sending email by logging

            entity.setStatus("SENT");
            entity.setSentAt(Instant.now());
            entityService.updateItem("EmailDispatch", Config.ENTITY_VERSION, UUID.fromString(technicalId), entity).get();

            // Update DigestRequest status to COMPLETED
            digestRequest.setStatus("COMPLETED");
            entityService.updateItem("DigestRequest", Config.ENTITY_VERSION, UUID.fromString(digestRequestId), digestRequest).get();

            return serializer.responseBuilder(request).success().build();

        } catch (Exception e) {
            logger.error("Exception in processEmailDispatch for technicalId {}: {}", technicalId, e.getMessage(), e);
            try {
                entity.setStatus("FAILED");
                entityService.updateItem("EmailDispatch", Config.ENTITY_VERSION, UUID.fromString(technicalId), entity).get();
            } catch (Exception ex) {
                logger.error("Failed to update EmailDispatch status to FAILED for technicalId {}: {}", technicalId, ex.getMessage(), ex);
            }
            return serializer.responseBuilder(request).failure(e.getMessage()).build();
        }
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        return processWithLogic(context);
    }

}
