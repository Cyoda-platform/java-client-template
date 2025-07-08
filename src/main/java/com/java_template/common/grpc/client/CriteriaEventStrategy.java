package com.java_template.common.grpc.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CriteriaChecker;
import com.java_template.common.workflow.CriteriaFactory;
import com.java_template.common.workflow.ModelKey;
import org.cyoda.cloud.api.event.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.EntityCriteriaCalculationResponse;
import io.cloudevents.v1.proto.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy for handling EntityCriteriaCalculationRequest events.
 * This strategy parses the CloudEvent, finds the appropriate CriteriaChecker,
 * and delegates directly to the criteria checker. The criteria checker handles its own
 * request/response conversion using serializers as needed.
 */
@Component
public class CriteriaEventStrategy implements EventHandlingStrategy {

    private static final Logger logger = LoggerFactory.getLogger(CriteriaEventStrategy.class);
    private static final String CRIT_CALC_REQ_EVENT_TYPE = "EntityCriteriaCalculationRequest";
    public static final String CRITERIA_EVENT_STRATEGY = "CriteriaEventStrategy";

    private final CriteriaFactory criteriaFactory;
    private final ObjectMapper objectMapper;

    public CriteriaEventStrategy(CriteriaFactory criteriaFactory, ObjectMapper objectMapper) {
        this.criteriaFactory = criteriaFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<Object> handleEvent(CloudEvent cloudEvent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("[IN] Received event {}: \n{}", CRIT_CALC_REQ_EVENT_TYPE, cloudEvent.getTextData());

                // Parse the request
                EntityCriteriaCalculationRequest request = objectMapper.readValue(cloudEvent.getTextData(), EntityCriteriaCalculationRequest.class);
                String criteriaName = request.getCriteriaName();

                logger.info("Processing {}: {}", CRIT_CALC_REQ_EVENT_TYPE, criteriaName);

                // Extract ModelKey from payload for criteria selection
                ObjectNode payloadData = (ObjectNode) request.getPayload().getData();
                ModelKey modelKey = ModelKey.extractFromPayload(payloadData);

                // Get criteria checker that supports this ModelKey
                CriteriaChecker criteriaChecker = criteriaFactory.getCriteriaForModel(criteriaName, modelKey);
                if (criteriaChecker == null) {
                    throw new IllegalArgumentException("No criteria checker found for name '" + criteriaName + "' that supports ModelKey " + modelKey);
                }

                // Delegate directly to criteria checker - it handles its own serialization
                CompletableFuture<EntityCriteriaCalculationResponse> futureResponse = criteriaChecker.check(request);
                try {
                    return futureResponse.get(); // Wait for completion
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Criteria checking was interrupted", e);
                } catch (Exception e) {
                    throw new RuntimeException("Error during criteria checking", e);
                }

            } catch (IOException e) {
                logger.error("Error handling criteria event: {}", cloudEvent, e);

                // Create error response
                try {
                    EntityCriteriaCalculationResponse errorResponse = objectMapper.readValue(cloudEvent.getTextData(), EntityCriteriaCalculationResponse.class);
                    errorResponse.setSuccess(false);
                    errorResponse.setMatches(false);
                    return errorResponse;
                } catch (Exception ex) {
                    logger.error("Error creating error response", ex);
                    return null;
                }
            }
        });
    }

    @Override
    public boolean supports(String eventType) {
        return CRIT_CALC_REQ_EVENT_TYPE.equals(eventType);
    }

    @Override
    public String getStrategyName() {
        return CRITERIA_EVENT_STRATEGY;
    }

}
