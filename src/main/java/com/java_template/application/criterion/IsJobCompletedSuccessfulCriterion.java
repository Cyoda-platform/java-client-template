package com.java_template.application.criterion;

import com.java_template.application.entity.PetIngestionJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IsJobCompletedSuccessfulCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsJobCompletedSuccessfulCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public boolean evaluate(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Evaluating IsJobCompletedSuccessfulCriterion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetIngestionJob.class)
            .map(this::checkCompletionStatus)
            .getOrDefault(false);
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean checkCompletionStatus(PetIngestionJob job) {
        boolean isCompleted = "COMPLETED".equals(job.getStatus()) && (job.getErrorMessage() == null || job.getErrorMessage().isEmpty());
        logger.debug("Job {} completion status: {}", job.getTechnicalId(), isCompleted);
        return isCompleted;
    }
}
