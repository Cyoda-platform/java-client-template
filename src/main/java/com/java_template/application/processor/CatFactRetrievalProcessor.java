package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for cat fact retrieval workflow transition.
 * Handles the retrieve transition (none → retrieved).
 * 
 * Business Logic:
 * - Calls Cat Fact API (https://catfact.ninja/fact)
 * - Retries up to 3 times with exponential backoff
 * - Extracts fact text from API response
 * - Sets source, retrievedDate, length, and isUsed
 * - Checks for duplicate facts in database
 */
@Component
public class CatFactRetrievalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactRetrievalProcessor.class);
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_DELAY = Duration.ofMillis(500);
    
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CatFactRetrievalProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        logger.debug("CatFactRetrievalProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing cat fact retrieval for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .map(ctx -> {
                CatFact catFact = ctx.entity();
                
                try {
                    // Retrieve fact from API with retries
                    String factText = retrieveFactWithRetries();
                    
                    // Check for duplicates
                    if (isDuplicateFact(factText)) {
                        logger.warn("Duplicate fact detected, retrieving new one");
                        factText = retrieveFactWithRetries();
                    }
                    
                    // Populate cat fact
                    catFact.setFactText(factText);
                    catFact.setSource("catfact.ninja");
                    catFact.setRetrievedDate(LocalDateTime.now());
                    catFact.setLength(factText.length());
                    catFact.setIsUsed(false);
                    catFact.setCategory("general");
                    
                    logger.info("Cat fact retrieved successfully: {} characters", factText.length());
                    return catFact;
                    
                } catch (Exception e) {
                    logger.error("Failed to retrieve cat fact: {}", e.getMessage());
                    throw new RuntimeException("Cat fact retrieval failed", e);
                }
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "CatFactRetrievalProcessor".equals(opSpec.operationName());
    }

    /**
     * Retrieves a cat fact from the API with exponential backoff retry logic.
     */
    private String retrieveFactWithRetries() throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return retrieveFactFromApi();
            } catch (Exception e) {
                lastException = e;
                logger.warn("Cat fact API call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    long delayMs = INITIAL_DELAY.toMillis() * (1L << (attempt - 1)); // Exponential backoff
                    logger.debug("Retrying in {}ms", delayMs);
                    Thread.sleep(delayMs);
                }
            }
        }
        
        throw new RuntimeException("Failed to retrieve cat fact after " + MAX_RETRIES + " attempts", lastException);
    }

    /**
     * Makes the actual API call to retrieve a cat fact.
     */
    private String retrieveFactFromApi() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(CAT_FACT_API_URL))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("API returned status: " + response.statusCode());
        }
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        String factText = jsonResponse.get("fact").asText();
        
        if (factText == null || factText.trim().isEmpty()) {
            throw new RuntimeException("Empty fact text received from API");
        }
        
        return factText.trim();
    }

    /**
     * Checks if the fact text already exists in the database.
     */
    private boolean isDuplicateFact(String factText) {
        try {
            // Create a simple condition to search for existing facts with the same text
            // This is a simplified check - in a real implementation you might want more sophisticated duplicate detection
            CompletableFuture<List<org.cyoda.cloud.api.event.common.DataPayload>> existingFacts = 
                entityService.getItemsByCondition(
                    CatFact.ENTITY_NAME, 
                    CatFact.ENTITY_VERSION, 
                    new java.util.HashMap<String, Object>() {{
                        put("factText", factText);
                    }}, 
                    false
                );
            
            List<org.cyoda.cloud.api.event.common.DataPayload> facts = existingFacts.get();
            return facts != null && !facts.isEmpty();
            
        } catch (Exception e) {
            logger.warn("Failed to check for duplicate facts: {}", e.getMessage());
            return false; // Assume no duplicate if check fails
        }
    }
}
