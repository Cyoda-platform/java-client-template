package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

@Component
public class AdoptionNotifyProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionNotifyProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AdoptionNotifyProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing notification for adoption request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest req) {
        return req != null && req.getTechnicalId() != null && !req.getTechnicalId().isEmpty();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest req = context.entity();
        try {
            // This processor is responsible for notifying the requester. In this prototype we only mark the request completed if not already.
            String status = req.getStatus();
            if (status == null) {
                return req;
            }

            if ("COMPLETED".equals(status)) {
                logger.info("AdoptionRequest {} already COMPLETED, skipping notification", req.getTechnicalId());
                return req;
            }

            // simulate sending notification (email/SMS) - in a real impl this would call a notification service
            logger.info("Notifying requester {} for adoption request {} (status={})", req.getRequesterContact(), req.getTechnicalId(), status);

            // Mark completed if this transition denotes completion
            req.setStatus("COMPLETED");
            try {
                req.setDecisionAt(Instant.now());
            } catch (Throwable ignore) {
            }

            return req;
        } catch (Exception e) {
            logger.error("Error while notifying for adoption request {}", req == null ? "<null>" : req.getTechnicalId(), e);
            return req;
        }
    }
}
