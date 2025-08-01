package com.java_template.application.processor;

import com.java_template.application.entity.HappyMailJob;
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

@Component
public class ProcessHappyMailJob implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessHappyMailJob(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HappyMailJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(HappyMailJob.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HappyMailJob entity) {
        return entity != null && entity.isValid();
    }

    private HappyMailJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HappyMailJob> context) {
        HappyMailJob entity = context.entity();
        String technicalId = entity.getMailTechnicalId();

        logger.info("Processing HappyMailJob entity with technicalId {}", technicalId);

        if ("COMPLETED".equalsIgnoreCase(entity.getStatus())) {
            logger.info("HappyMailJob {} completed successfully: {}", technicalId, entity.getResultMessage());
        } else if ("FAILED".equalsIgnoreCase(entity.getStatus())) {
            logger.error("HappyMailJob {} failed: {}", technicalId, entity.getResultMessage());
        } else {
            logger.info("HappyMailJob {} in status: {}", technicalId, entity.getStatus());
        }

        return entity;
    }
}
