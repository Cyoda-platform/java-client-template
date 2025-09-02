package com.java_template.application.processor;

import com.java_template.application.entity.emailcampaign.version_1.EmailCampaign;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

@Component
public class EmailCampaignProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EmailCampaignProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    
    @Autowired
    private EntityService entityService;

    public EmailCampaignProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EmailCampaign for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(EmailCampaign.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EmailCampaign entity) {
        return entity != null && entity.isValid();
    }

    private EmailCampaign processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<EmailCampaign> context) {
        EmailCampaign entity = context.entity();
        
        // Generate ID if not set
        if (entity.getId() == null) {
            entity.setId("campaign-" + UUID.randomUUID().toString().substring(0, 8));
        }
        
        // Set campaign name if not provided
        if (entity.getCampaignName() == null || entity.getCampaignName().trim().isEmpty()) {
            entity.setCampaignName("Week of " + LocalDateTime.now().toLocalDate().toString());
        }
        
        // Set scheduled date to next Monday if not provided
        if (entity.getScheduledDate() == null) {
            LocalDateTime nextMonday = getNextMonday();
            entity.setScheduledDate(nextMonday);
        }
        
        // Select a cat fact if not provided
        if (entity.getCatFactId() == null) {
            String selectedFactId = selectAvailableCatFact();
            entity.setCatFactId(selectedFactId);
        }
        
        // Count active subscribers
        if (entity.getTotalSubscribers() == null) {
            int activeSubscriberCount = countActiveSubscribers();
            entity.setTotalSubscribers(activeSubscriberCount);
        }
        
        // Set default subject if not provided
        if (entity.getSubject() == null || entity.getSubject().trim().isEmpty()) {
            entity.setSubject("Your Weekly Cat Fact!");
        }
        
        // Set default email template
        if (entity.getEmailTemplate() == null || entity.getEmailTemplate().trim().isEmpty()) {
            entity.setEmailTemplate("weekly_cat_fact_template");
        }
        
        // Initialize send counters
        if (entity.getSuccessfulSends() == null) {
            entity.setSuccessfulSends(0);
        }
        if (entity.getFailedSends() == null) {
            entity.setFailedSends(0);
        }
        
        // Set sent date when campaign is completed
        String currentState = getCurrentState(context);
        if ("completed".equals(currentState) && entity.getSentDate() == null) {
            entity.setSentDate(LocalDateTime.now());
            updateCatFactUsage(entity.getCatFactId());
        }
        
        logger.info("Processed email campaign: {} scheduled for: {}", entity.getId(), entity.getScheduledDate());
        return entity;
    }
    
    private LocalDateTime getNextMonday() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMonday = now.with(DayOfWeek.MONDAY).plusWeeks(1).withHour(9).withMinute(0).withSecond(0);
        return nextMonday;
    }
    
    private String selectAvailableCatFact() {
        try {
            // Find ready cat facts
            Condition stateCondition = Condition.of("$.meta.state", "EQUALS", "ready");
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(stateCondition));
            
            List<EntityResponse<CatFact>> readyFacts = entityService.getItemsByCondition(
                CatFact.class, CatFact.ENTITY_NAME, CatFact.ENTITY_VERSION, condition, true);
            
            if (!readyFacts.isEmpty()) {
                // Select the fact with lowest usage count
                EntityResponse<CatFact> selectedFact = readyFacts.stream()
                    .min((f1, f2) -> Integer.compare(f1.getData().getUsageCount(), f2.getData().getUsageCount()))
                    .orElse(readyFacts.get(0));
                return selectedFact.getData().getId();
            }
        } catch (Exception e) {
            logger.error("Error selecting cat fact: {}", e.getMessage(), e);
        }
        return "default-fact-id"; // Fallback
    }
    
    private int countActiveSubscribers() {
        try {
            Condition activeCondition = Condition.of("$.isActive", "EQUALS", true);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(activeCondition));
            
            List<EntityResponse<Subscriber>> activeSubscribers = entityService.getItemsByCondition(
                Subscriber.class, Subscriber.ENTITY_NAME, Subscriber.ENTITY_VERSION, condition, true);
            
            return activeSubscribers.size();
        } catch (Exception e) {
            logger.error("Error counting active subscribers: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    private String getCurrentState(ProcessorSerializer.ProcessorEntityExecutionContext<EmailCampaign> context) {
        // This would typically come from the context or request metadata
        return "draft"; // Default state
    }
    
    private void updateCatFactUsage(String catFactId) {
        try {
            // Find the cat fact and update its usage
            EntityResponse<CatFact> factResponse = entityService.getItem(UUID.fromString(catFactId), CatFact.class);
            CatFact fact = factResponse.getData();
            fact.setUsageCount(fact.getUsageCount() + 1);
            fact.setLastUsedDate(LocalDateTime.now());
            
            entityService.update(factResponse.getMetadata().getId(), fact, null);
            logger.info("Updated cat fact usage: {}", catFactId);
        } catch (Exception e) {
            logger.error("Error updating cat fact usage: {}", e.getMessage(), e);
        }
    }
}
