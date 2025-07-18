package com.java_template.application.processor;

import com.java_template.application.entity.Job;
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
public class JobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public JobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("JobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Job.class)
                .withErrorHandler(this::handleJobError)
                .validate(this::isValidJob, "Invalid Job entity state")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "JobProcessor".equals(modelSpec.operationName()) &&
               "job".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidJob(Job job) {
        return job != null && job.isValid();
    }

    private ErrorInfo handleJobError(Throwable throwable, Job job) {
        logger.error("Error processing Job entity", throwable);
        return new ErrorInfo("JobProcessingError", throwable.getMessage());
    }

    private Job processBusinessLogic(Job job) {
        // Example business logic: update status if activityCount > 0
        if (job.getActivityCount() > 0 && !"active".equalsIgnoreCase(job.getStatus())) {
            job.setStatus("active");
        }
        return job;
    }
}
