package com.java_template.application.processor;

import com.java_template.application.entity.FetchJob;
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

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FetchJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public FetchJobProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("FetchJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FetchJob.class)
            .validate(this::isValidEntity, "Invalid FetchJob entity state")
            .map(this::processFetchJobLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "FetchJobProcessor".equals(modelSpec.operationName()) &&
               "fetchjob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(FetchJob fetchJob) {
        return fetchJob != null && fetchJob.isValid();
    }

    private FetchJob processFetchJobLogic(FetchJob fetchJob) {
        // Step 1: Fetch NBA scores from external API for scheduledDate
        // Note: External API call not implemented here; just simulate with dummy data
        logger.info("Fetching NBA scores for scheduledDate: {}", fetchJob.getScheduledDate());

        // Step 2: Save fetched game data locally - simulate by setting resultSummary
        String resultSummary = "20 games fetched"; // example summary
        fetchJob.setResultSummary(resultSummary);

        // Step 3: Update status to COMPLETED (simulate success)
        fetchJob.setStatus(FetchJob.StatusEnum.COMPLETED);

        // Step 4: Trigger Notifications for all ACTIVE Subscribers
        // Fetch Subscribers with ACTIVE status
        List<com.java_template.application.entity.Subscriber> activeSubscribers =
            entityService.searchSubscribersByStatus(com.java_template.application.entity.Subscriber.StatusEnum.ACTIVE);

        // Create Notification entities with PENDING status for each active subscriber
        List<com.java_template.application.entity.Notification> notifications = activeSubscribers.stream()
            .map(subscriber -> {
                com.java_template.application.entity.Notification notification = new com.java_template.application.entity.Notification();
                notification.setId(java.util.UUID.randomUUID().toString());
                notification.setSubscriberId(subscriber.getId());
                notification.setJobId(fetchJob.getId());
                notification.setStatus(com.java_template.application.entity.Notification.StatusEnum.PENDING);
                return notification;
            })
            .collect(Collectors.toList());

        entityService.addNotifications(notifications);

        return fetchJob;
    }
}
