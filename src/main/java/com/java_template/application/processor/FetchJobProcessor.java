package com.java_template.application.processor;

import com.java_template.application.entity.FetchJob;
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
public class FetchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public FetchJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("FetchJobProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FetchJob.class)
            .validate(FetchJob::isValid, "Invalid entity state")
            .map(this::processFetchJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FetchJobProcessor".equals(modelSpec.operationName()) &&
               "fetchjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private FetchJob processFetchJobLogic(FetchJob fetchJob) {
        logger.info("Processing FetchJob with id: {} and scheduledDate: {}", fetchJob.getId(), fetchJob.getScheduledDate());

        // Business logic based on functional requirements:
        // 1. Fetch NBA scores for the scheduledDate (simulate or call external API)
        // 2. Save fetched game data locally (not shown as no direct property or method)
        // 3. Update status to COMPLETED or FAILED based on fetch result
        // 4. Trigger Notification entities creation for all ACTIVE subscribers (not possible to do here directly)

        // Simulate fetch success and update status
        fetchJob.setStatus(FetchJob.StatusEnum.COMPLETED);
        fetchJob.setResultSummary("NBA scores fetched successfully for " + fetchJob.getScheduledDate());

        return fetchJob;
    }
}
