package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequestJob;
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
public class DigestRequestJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public DigestRequestJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestRequestJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequestJob for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(DigestRequestJob.class)
                .validate(DigestRequestJob::isValid, "Invalid DigestRequestJob entity state")
                .map(this::processDigestRequestJob)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestJobProcessor".equals(modelSpec.operationName()) &&
                "digestrequestjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequestJob processDigestRequestJob(DigestRequestJob entity) {
        // Business logic copied from processDigestRequestJob() flow in functional requirement

        // 1. Initial State: DigestRequestJob created with PENDING status (already set before processing)

        // 2. Registration: Log the event and update status to PROCESSING.
        logger.info("DigestRequestJob {} status updated to PROCESSING", entity.getId());
        entity.setStatus(DigestRequestJob.StatusEnum.PROCESSING);

        // 3. Data Retrieval: Trigger creation of DigestData entity by calling external petstore API based on metadata or defaults.
        //    We assume here the creation of DigestData entity is done downstream and not within this processor.

        // 4. Completion: Update DigestRequestJob status to COMPLETED or FAILED depending on downstream results.
        //    This processor does not directly finalize the job; it only marks it PROCESSING.

        return entity;
    }
}
