package com.java_template.application.processor;

import com.java_template.application.entity.ScoreFetchJob;
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

import java.time.Instant;

@Component
public class StartScoreFetchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public StartScoreFetchJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("StartScoreFetchJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ScoreFetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(ScoreFetchJob.class)
                .map(job -> {
                    job.setStatus(com.java_template.application.entity.StatusEnum.RUNNING.name());
                    job.setTriggeredAt(Instant.now());
                    return job;
                })
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "StartScoreFetchJobProcessor".equals(modelSpec.operationName()) &&
                "scorefetchjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
