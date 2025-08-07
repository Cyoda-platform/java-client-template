package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Subscriber;
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

import java.util.concurrent.CompletableFuture;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public NotifySubscribersProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        logger.info("Notifying active subscribers for Job");
        int notifiedCount = 0;
        try {
            CompletableFuture<ArrayNode> allSubsFuture = entityService.getItems(Subscriber.ENTITY_NAME, "1");
            ArrayNode allSubs = allSubsFuture.get();
            if (allSubs == null) return job;

            for (JsonNode node : allSubs) {
                Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                if (subscriber.getActive() != null && subscriber.getActive()) {
                    try {
                        String contactValue = subscriber.getContactValue();
                        logger.info("Notifying subscriber at {}", contactValue);
                        // In real implementation, send email or webhook call here
                        notifiedCount++;
                    } catch (Exception e) {
                        logger.error("Failed to notify subscriber: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Exception during subscriber notification: {}", ex.getMessage());
        }
        logger.info("Notified {} subscribers for Job", notifiedCount);
        return job;
    }

}
