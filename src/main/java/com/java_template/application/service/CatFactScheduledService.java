package com.java_template.application.service;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.application.entity.event.version_1.Event;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CatFactScheduledService - Handles weekly cat fact ingestion and email sending
 * 
 * This service:
 * 1. Fetches a random cat fact from the API weekly
 * 2. Sends the fact to all active subscribers
 * 3. Tracks events for reporting
 */
@Service
public class CatFactScheduledService {

    private static final Logger logger = LoggerFactory.getLogger(CatFactScheduledService.class);
    private final EntityService entityService;
    private final RestTemplate restTemplate;

    public CatFactScheduledService(EntityService entityService, RestTemplate restTemplate) {
        this.entityService = entityService;
        this.restTemplate = restTemplate;
    }

    /**
     * Scheduled task to run weekly (every Monday at 8 AM)
     * Cron: 0 0 8 ? * MON (Monday at 8:00 AM)
     */
    @Scheduled(cron = "0 0 8 ? * MON")
    public void fetchAndSendWeeklyCatFact() {
        logger.info("Starting weekly cat fact ingestion and send");
        
        try {
            // Step 1: Fetch cat fact from API
            CatFact catFact = fetchCatFactFromAPI();
            if (catFact == null) {
                logger.error("Failed to fetch cat fact from API");
                return;
            }

            // Step 2: Save cat fact to database
            EntityWithMetadata<CatFact> savedFact = entityService.create(catFact);
            logger.info("Cat fact saved: {}", savedFact.metadata().getId());

            // Step 3: Get all active subscribers
            List<EntityWithMetadata<Subscriber>> activeSubscribers = getActiveSubscribers();
            logger.info("Found {} active subscribers", activeSubscribers.size());

            // Step 4: Send to each subscriber and track events
            for (EntityWithMetadata<Subscriber> subscriberWithMeta : activeSubscribers) {
                Subscriber subscriber = subscriberWithMeta.entity();
                sendEmailToSubscriber(subscriber, catFact);
                trackEmailSentEvent(subscriber.getEmail(), savedFact.metadata().getId().toString());
            }

            logger.info("Weekly cat fact campaign completed successfully");
        } catch (Exception e) {
            logger.error("Error in weekly cat fact ingestion", e);
        }
    }

    private CatFact fetchCatFactFromAPI() {
        try {
            // Call Cat Fact API
            String apiUrl = "https://catfact.ninja/fact";
            Map<String, Object> response = restTemplate.getForObject(apiUrl, Map.class);
            
            if (response != null && response.containsKey("fact")) {
                CatFact catFact = new CatFact();
                catFact.setFactId("fact-" + UUID.randomUUID());
                catFact.setText((String) response.get("fact"));
                catFact.setRetrievedAt(LocalDateTime.now());
                catFact.setCampaignId("campaign-" + LocalDateTime.now().toLocalDate());
                return catFact;
            }
        } catch (Exception e) {
            logger.error("Error fetching cat fact from API", e);
        }
        return null;
    }

    private List<EntityWithMetadata<Subscriber>> getActiveSubscribers() {
        ModelSpec modelSpec = new ModelSpec().withName(Subscriber.ENTITY_NAME).withVersion(Subscriber.ENTITY_VERSION);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        SimpleCondition statusCondition = new SimpleCondition()
                .withJsonPath("$.status")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree("active"));

        GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(statusCondition));

        return entityService.search(modelSpec, condition, Subscriber.class,
                com.java_template.common.repository.SearchAndRetrievalParams.builder()
                        .pageSize(1000)
                        .inMemory(true)
                        .build()).data();
    }

    private void sendEmailToSubscriber(Subscriber subscriber, CatFact catFact) {
        logger.info("Sending cat fact to subscriber: {}", subscriber.getEmail());
        // Email sending logic would be implemented here
        // For now, just log the action
    }

    private void trackEmailSentEvent(String email, String factId) {
        Event event = new Event();
        event.setEventId("event-" + UUID.randomUUID());
        event.setType("email_sent");
        event.setTimestamp(LocalDateTime.now());
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("subscriberEmail", email);
        metadata.put("factId", factId);
        event.setMetadata(metadata);

        entityService.create(event);
    }
}

