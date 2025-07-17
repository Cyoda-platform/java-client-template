package com.java_template.application.processor;

import com.java_template.application.entity.JobStatus;
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

@Component
public class JobStatusProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public JobStatusProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("JobStatusProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing JobStatus for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(JobStatus.class)
                .withErrorHandler(this::handleJobStatusError)
                .validate(JobStatus::isValid, "Invalid JobStatus entity state")
                // No transformations or additional validation logic given in prototype
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "JobStatusProcessor".equals(modelSpec.operationName()) &&
               "jobStatus".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleJobStatusError(Throwable throwable, JobStatus entity) {
        logger.error("Error processing JobStatus entity", throwable);
        return new ErrorInfo("JobStatusError", throwable.getMessage());
    }
}
