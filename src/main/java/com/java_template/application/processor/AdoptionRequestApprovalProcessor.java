package com.java_template.application.processor;

import com.java_template.application.entity.AdoptionRequest;
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

import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class AdoptionRequestApprovalProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public AdoptionRequestApprovalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("AdoptionRequestApprovalProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(AdoptionRequest.class)
                .withErrorHandler(this::handleAdoptionRequestError)
                .validate(this::isValidAdoptionRequest, "Invalid AdoptionRequest state")
                .map(this::approveAdoptionRequest)
                .validate(this::businessValidation, "Failed business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestApprovalProcessor".equals(modelSpec.operationName()) &&
                "adoptionRequest".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidAdoptionRequest(AdoptionRequest adoptionRequest) {
        return adoptionRequest != null && adoptionRequest.getRequestId() != null && adoptionRequest.getPetId() != null;
    }

    private ErrorInfo handleAdoptionRequestError(Throwable t, AdoptionRequest adoptionRequest) {
        logger.error("Error processing AdoptionRequest: {}", t.getMessage(), t);
        return new ErrorInfo(t.getMessage(), "ADOPTION_REQUEST_PROCESSING_ERROR");
    }

    private AdoptionRequest approveAdoptionRequest(AdoptionRequest adoptionRequest) {
        adoptionRequest.setStatus("approved");
        return adoptionRequest;
    }

    private boolean businessValidation(AdoptionRequest adoptionRequest) {
        return "approved".equals(adoptionRequest.getStatus());
    }
}
