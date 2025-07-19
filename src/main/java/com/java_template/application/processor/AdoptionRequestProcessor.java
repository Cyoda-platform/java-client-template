package com.java_template.application.processor;

import com.java_template.application.entity.PurrfectPetsJob;
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
public class AdoptionRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public AdoptionRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("AdoptionRequestProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PurrfectPetsJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(PurrfectPetsJob.class)
                .validate(PurrfectPetsJob::isValid, "Invalid entity state")
                .map(this::processPurrfectPetsJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestProcessor".equals(modelSpec.operationName()) &&
                "purrfectPetsJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private PurrfectPetsJob processPurrfectPetsJob(PurrfectPetsJob job) {
        logger.info("Processing PurrfectPetsJob with jobId: {}", job.getJobId());
        if (!"PetDataSync".equals(job.getJobType()) && !"AdoptionProcessing".equals(job.getJobType())) {
            job.setStatus("FAILED");
            logger.error("Invalid jobType: {}", job.getJobType());
            return job;
        }
        job.setStatus("PROCESSING");

        try {
            if ("PetDataSync".equals(job.getJobType())) {
                logger.info("Syncing pet data from Petstore API...");
            } else if ("AdoptionProcessing".equals(job.getJobType())) {
                logger.info("Processing adoption requests...");
            }
            job.setStatus("COMPLETED");
            logger.info("Job {} completed successfully", job.getJobId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            logger.error("Job {} failed processing: {}", job.getJobId(), e.getMessage());
        }
        return job;
    }

}
