package com.java_template.common.grpc.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.workflow.*;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.springframework.stereotype.Component;

@Component
public class CriteriaEventStrategy extends AbstractEventStrategy<EntityCriteriaCalculationRequest, EntityCriteriaCalculationResponse, OperationSpecification.Criterion> {

    private static final String CRIT_CALC_REQ_EVENT_TYPE = "EntityCriteriaCalculationRequest";
    public static final String CRITERIA_EVENT_STRATEGY = "CriteriaEventStrategy";

    public CriteriaEventStrategy(
            OperationFactory operationFactory,
            ObjectMapper objectMapper,
            CyodaContextFactory eventContextFactory
    ) {
        super(operationFactory, objectMapper, eventContextFactory);
    }


    @Override
    protected Class<EntityCriteriaCalculationRequest> getRequestClass() {
        return EntityCriteriaCalculationRequest.class;
    }

    @Override
    protected OperationSpecification.Criterion createOperationSpecification(
            EntityCriteriaCalculationRequest request
    ) throws JsonProcessingException {
        EntityMetadata modelKey = parseForModelKey(request.getPayload().getMeta());
        return OperationSpecification.create(request, modelKey);
    }


    @Override
    protected EntityCriteriaCalculationResponse executeOperation(
            OperationSpecification.Criterion operation,
            EntityCriteriaCalculationRequest request,
            CyodaEventContext<EntityCriteriaCalculationRequest> context
    ) {
        // Get a criterion checker that supports this OperationSpecification
        CyodaCriterion cyodaCriterion = operationFactory.getCriteriaForModel(operation);
        // Delegate directly to criteria checker - it handles its own serialization
        return cyodaCriterion.check(context);
    }

    @Override
    protected EntityCriteriaCalculationResponse createErrorResponse() {
        return new EntityCriteriaCalculationResponse();
    }

    @Override
    protected void setRequestIdInErrorResponse(EntityCriteriaCalculationResponse errorResponse, String requestId) {
        errorResponse.setRequestId(requestId);
    }

    @Override
    protected void enrichErrorResponse(EntityCriteriaCalculationResponse errorResponse) {
        errorResponse.setMatches(false);
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
