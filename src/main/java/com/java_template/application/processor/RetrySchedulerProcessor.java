package com.java_template.application.processor;

import com.java_template.application.entity.lookupjob.version_1.LookupJob;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

@Component
public class RetrySchedulerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetrySchedulerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final Random random = new Random();

    public RetrySchedulerProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing RetrySchedulerProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(LookupJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(LookupJob entity) {
        return entity != null && entity.isValid();
    }

    private LookupJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<LookupJob> context) {
        LookupJob job = context.entity();
        try {
            int attempts = job.getAttempts() != null ? job.getAttempts() : 0;
            attempts = attempts + 1;
            job.setAttempts(attempts);
            job.setLastAttemptAt(Instant.now().toString());

            int maxAttempts = Config.MAX_ATTEMPTS;
            if (attempts < maxAttempts) {
                // compute exponential backoff in seconds with jitter
                long baseDelaySecs = (1L << (attempts - 1)); // 1,2,4,...
                long jitter = random.nextInt(1000); // milliseconds jitter
                long delayMs = Math.min(baseDelaySecs * 1000L + jitter, Config.MAX_BACKOFF_MS);
                // schedule is conceptual: in this environment we cannot schedule real timed events, so we log intent
                logger.info("RetrySchedulerProcessor: scheduling retry for job={} after {}ms (attempts={})", job.getTechnicalId(), delayMs, attempts);
                // In a real implementation we'd publish a delayed event to re-run the workflow. Here we rely on orchestration platform.
            } else {
                logger.info("RetrySchedulerProcessor: max attempts reached for job={} attempts={}", job.getTechnicalId(), attempts);
                // On reaching max attempts the workflow transitions to PersistErrorProcessor as per workflow
            }
        } catch (Exception e) {
            logger.error("RetrySchedulerProcessor: error scheduling retry for job={}: {}", job.getTechnicalId(), e.getMessage());
        }
        return job;
    }
}
