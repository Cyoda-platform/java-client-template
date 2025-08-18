package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.util.ArrayList;
import java.util.List;

@Component
public class FetchAndTransformProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchAndTransformProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FetchAndTransformProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchAndTransform for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job for fetch/transform")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getSourceUrl() != null && !job.getSourceUrl().isBlank();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Simulated fetch-transform loop. In real implementation integrate with HTTP client and paging
        try {
            int max = 10; // placeholder for demonstration
            List<Laureate> transformed = new ArrayList<>();
            for (int i = 0; i < max; i++) {
                Laureate l = new Laureate();
                l.setFullName("Laureate " + i);
                l.setYear("2024");
                l.setCategory("physics");
                // minimal normalization
                transformed.add(l);
                // In real code persist staging record referencing job.runId
            }
            logger.info("Transformed {} laureates for job {}", transformed.size(), job.getTechnicalId());
        } catch (Exception e) {
            logger.error("Error during fetch/transform for job {}: {}", job.getTechnicalId(), e.getMessage());
        }
        return job;
    }
}
