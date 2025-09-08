package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CatFactIngestionProcessor - Retrieves cat facts from external API
 * 
 * Input: Empty or trigger data
 * Purpose: Retrieve cat facts from external API
 * Output: CatFact entity in RETRIEVED state
 */
@Component
public class CatFactIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactIngestionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact ingestion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CatFact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cat fact for ingestion")
                .map(this::processCatFactIngestion)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for ingestion
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CatFact> entityWithMetadata) {
        CatFact catFact = entityWithMetadata.entity();
        
        return catFact != null && 
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for cat fact ingestion
     * This simulates calling the Cat Fact API (https://catfact.ninja/fact)
     */
    private EntityWithMetadata<CatFact> processCatFactIngestion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact catFact = entityWithMetadata.entity();

        logger.debug("Ingesting cat fact from external API");

        // Simulate API call to Cat Fact API
        // In a real implementation, this would make an HTTP call to https://catfact.ninja/fact
        String factText = simulateApiCall();
        
        // Generate unique factId
        String factId = "fact_" + UUID.randomUUID().toString().substring(0, 8);
        
        // Set cat fact data
        catFact.setFactId(factId);
        catFact.setText(factText);
        catFact.setLength(factText.length());
        catFact.setRetrievedDate(LocalDateTime.now());
        catFact.setSource("catfact.ninja");
        catFact.setIsUsed(false);
        catFact.setUsageCount(0);
        catFact.setLastUsedDate(null);
        
        logger.info("Cat fact ingested successfully with ID: {}", factId);

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }

    /**
     * Simulates calling the Cat Fact API
     * In a real implementation, this would make an HTTP call to https://catfact.ninja/fact
     */
    private String simulateApiCall() {
        // Sample cat facts for simulation
        String[] sampleFacts = {
            "Cats have 32 muscles in each ear.",
            "A cat's hearing is better than a dog's.",
            "Cats can rotate their ears 180 degrees.",
            "A cat has been mayor of Talkeetna, Alaska, for 15 years.",
            "Cats sleep 70% of their lives.",
            "A group of cats is called a clowder.",
            "Cats have a third eyelid called a nictitating membrane.",
            "A cat's nose pad is ridged with a unique pattern, just like a human fingerprint."
        };
        
        int randomIndex = (int) (Math.random() * sampleFacts.length);
        return sampleFacts[randomIndex];
    }
}
