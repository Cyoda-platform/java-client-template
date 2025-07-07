package com.java_template.entity.weeklycatfact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("weeklycatfact")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    // This variable will hold the last fetched fact for condition checking
    private static final String FACT_FIELD = "fact";

    // This variable will hold email sending result for condition checking
    private static final String EMAIL_SENT_SUCCESS_FIELD = "emailSentSuccess";

    public CompletableFuture<ObjectNode> triggerFetchCatFact(ObjectNode entity) {
        logger.info("Triggering fetch cat fact");
        // No change to entity here, just pass it along
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> fetchCatFact(ObjectNode entity) {
        logger.info("Fetching cat fact from external API");
        return CompletableFuture.supplyAsync(() -> {
            try {
                String jsonStr = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
                if (jsonStr == null || jsonStr.isEmpty()) {
                    logger.error("Empty response from CatFact API");
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Empty response from CatFact API");
                }
                JsonNode json = objectMapper.readTree(jsonStr);
                String fact = json.path(FACT_FIELD).asText(null);
                if (fact == null || fact.isBlank()) {
                    logger.error("Empty cat fact received");
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Empty cat fact received");
                }
                // Store fact in entity for next steps and condition checks
                entity.put(FACT_FIELD, fact);
                logger.info("Cat fact fetched successfully: {}", fact);
                return entity;
            } catch (Exception e) {
                logger.error("Failed to fetch cat fact from external API", e);
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
            }
        });
    }

    public CompletableFuture<ObjectNode> sendEmailsToSubscribers(ObjectNode entity) {
        logger.info("Sending emails to subscribers");
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION).thenCompose(subscribers -> {
            List<CompletableFuture<Void>> emailFutures = new ArrayList<>();
            String fact = entity.path(FACT_FIELD).asText("");
            for (JsonNode subscriber : subscribers) {
                String name = subscriber.path("name").asText("");
                String email = subscriber.path("email").asText("");
                if (email != null && !email.isBlank()) {
                    emailFutures.add(sendEmailAsync(name, email, fact));
                }
            }
            return CompletableFuture.allOf(emailFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        entity.put("sentAt", Instant.now().toString());
                        entity.put(EMAIL_SENT_SUCCESS_FIELD, true);
                        logger.info("All emails sent successfully");
                        return entity;
                    });
        }).exceptionally(ex -> {
            logger.error("Failed to send emails", ex);
            entity.put(EMAIL_SENT_SUCCESS_FIELD, false);
            return entity;
        });
    }

    // Condition function: returns true if fact fetched successfully (fact present and not blank)
    public CompletableFuture<ObjectNode> isFactFetchedSuccessfully(ObjectNode entity) {
        boolean success = entity.hasNonNull(FACT_FIELD) && !entity.path(FACT_FIELD).asText().isBlank();
        entity.put("factFetchSuccess", success);
        logger.info("isFactFetchedSuccessfully: {}", success);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: returns true if fact fetch failed
    public CompletableFuture<ObjectNode> isFactFetchedUnsuccessfully(ObjectNode entity) {
        boolean failure = !entity.hasNonNull(FACT_FIELD) || entity.path(FACT_FIELD).asText().isBlank();
        entity.put("factFetchSuccess", !failure);
        logger.info("isFactFetchedUnsuccessfully: {}", failure);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: returns true if emails sent successfully
    public CompletableFuture<ObjectNode> areEmailsSentSuccessfully(ObjectNode entity) {
        boolean success = entity.path(EMAIL_SENT_SUCCESS_FIELD).asBoolean(false);
        logger.info("areEmailsSentSuccessfully: {}", success);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition function: returns true if emails sending failed
    public CompletableFuture<ObjectNode> areEmailsSentUnsuccessfully(ObjectNode entity) {
        boolean failure = !entity.path(EMAIL_SENT_SUCCESS_FIELD).asBoolean(false);
        logger.info("areEmailsSentUnsuccessfully: {}", failure);
        return CompletableFuture.completedFuture(entity);
    }

    // Mock sendEmailAsync function
    private CompletableFuture<Void> sendEmailAsync(String name, String email, String fact) {
        // TODO: Replace with actual email sending logic
        logger.info("Sending email to {} <{}> with fact: {}", name, email, fact);
        return CompletableFuture.completedFuture(null);
    }

    // Injected or mocked entityService - assume exists with getItems method
    // TODO: Replace with actual implementation or injection
    private final EntityService entityService = new EntityService();

    // Mock EntityService class for compilation
    private static class EntityService {
        public CompletableFuture<List<JsonNode>> getItems(String entityName, String entityVersion) {
            // TODO: Implement fetching subscriber list
            return CompletableFuture.completedFuture(List.of());
        }
    }
}