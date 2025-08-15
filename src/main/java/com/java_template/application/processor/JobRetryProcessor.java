package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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
public class JobRetryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobRetryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobRetryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing JobRetryProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job for retry processing")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && "FAILED".equals(job.getStatus());
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        Integer retryCount = job.getRetryCount() == null ? 0 : job.getRetryCount();
        Integer maxRetries = job.getMaxRetries() == null ? 3 : job.getMaxRetries();

        if (retryCount < maxRetries) {
            retryCount++;
            job.setRetryCount(retryCount);
            job.setStatus("RETRY_WAIT");
            // compute next retry time using simple exponential backoff (seconds)
            long backoffSeconds = (long) Math.pow(2, retryCount);
            job.setLastRunAt(Instant.now().plusSeconds(backoffSeconds).toString());
            logger.info("Job {} scheduled retry {} (backoff {}s)", job.getTechnicalId(), retryCount, backoffSeconds);
        } else {
            job.setStatus("ESCALATED");
            logger.info("Job {} reached max retries and is escalated", job.getTechnicalId());
        }

        return job;
    }
}
