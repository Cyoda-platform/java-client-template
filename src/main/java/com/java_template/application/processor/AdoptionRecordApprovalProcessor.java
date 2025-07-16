package com.java_template.application.processor;

import com.java_template.application.entity.AdoptionRecord;
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
public class AdoptionRecordApprovalProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public AdoptionRecordApprovalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("AdoptionRecordApprovalProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRecord approval for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(AdoptionRecord.class)
                .withErrorHandler(this::handleAdoptionRecordError)
                .validate(this::isValidForApproval, "Invalid adoption record approval state")
                .map(this::markAsApproved)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRecordApprovalProcessor".equals(modelSpec.operationName()) &&
               "adoptionRecord".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidForApproval(AdoptionRecord record) {
        return "pending_approval".equals(record.getStatus());
    }

    private AdoptionRecord markAsApproved(AdoptionRecord record) {
        record.setStatus("approved");
        return record;
    }

    private ErrorInfo handleAdoptionRecordError(Throwable throwable, AdoptionRecord record) {
        logger.error("Error processing AdoptionRecord approval: {}", record, throwable);
        return new ErrorInfo("PROCESSING_ERROR", throwable.getMessage());
    }
}
