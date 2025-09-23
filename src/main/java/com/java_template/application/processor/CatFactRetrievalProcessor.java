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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CatFactRetrievalProcessor - Handles cat fact retrieval from external APIs
 * 
 * This processor simulates retrieving cat facts from external APIs and
 * populates the CatFact entity with retrieved data and metadata.
 */
@Component
public class CatFactRetrievalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactRetrievalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactRetrievalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact retrieval for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CatFact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid CatFact entity wrapper")
                .map(this::processRetrievalLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CatFact> entityWithMetadata) {
        CatFact entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    /**
     * Main business logic for cat fact retrieval
     * Simulates external API call and populates fact data
     */
    private EntityWithMetadata<CatFact> processRetrievalLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact entity = entityWithMetadata.entity();

        logger.debug("Retrieving cat fact for: {}", entity.getFactId());

        // Simulate external API call to retrieve cat fact
        String retrievedContent = retrieveCatFactFromAPI();
        
        // Populate entity with retrieved data
        entity.setContent(retrievedContent);
        entity.setSource("Cat Facts API");
        entity.setRetrievedDate(LocalDateTime.now());
        entity.setIsUsed(false);
        entity.setContentLength(retrievedContent.length());
        entity.setLanguage("en");
        entity.setCategory("general");

        // Set metadata
        CatFact.CatFactMetadata metadata = new CatFact.CatFactMetadata();
        metadata.setApiEndpoint("https://catfact.ninja/fact");
        metadata.setApiVersion("1.0");
        metadata.setQualityScore(calculateQualityScore(retrievedContent));
        metadata.setIsVerified(true);
        metadata.setOriginalId(UUID.randomUUID().toString());
        metadata.setRetrievalMethod("API");
        entity.setMetadata(metadata);

        // Update timestamps
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }

        logger.info("CatFact {} retrieved successfully with content length: {}", 
                   entity.getFactId(), entity.getContentLength());

        return entityWithMetadata;
    }

    /**
     * Simulates external API call to retrieve cat fact
     * In a real implementation, this would make HTTP calls to external APIs
     */
    private String retrieveCatFactFromAPI() {
        // Simulate API response with sample cat facts
        String[] sampleFacts = {
            "Cats have five toes on their front paws, but only four toes on their back paws.",
            "A group of cats is called a clowder.",
            "Cats can rotate their ears 180 degrees.",
            "A cat's purr vibrates at a frequency that promotes bone healing.",
            "Cats sleep for 12 to 16 hours a day.",
            "A cat's nose print is unique, much like a human's fingerprint.",
            "Cats have a third eyelid called a nictitating membrane.",
            "The first cat in space was a French cat named Felicette in 1963."
        };
        
        int randomIndex = (int) (Math.random() * sampleFacts.length);
        return sampleFacts[randomIndex];
    }

    /**
     * Calculates a quality score for the retrieved cat fact
     */
    private Double calculateQualityScore(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0.0;
        }
        
        // Simple quality scoring based on content length and characteristics
        double score = 0.5; // Base score
        
        // Length scoring
        int length = content.length();
        if (length > 50 && length < 200) {
            score += 0.3; // Good length
        } else if (length >= 200) {
            score += 0.2; // Acceptable length
        }
        
        // Content quality indicators
        if (content.toLowerCase().contains("cat")) {
            score += 0.2; // Contains "cat"
        }
        
        return Math.min(score, 1.0); // Cap at 1.0
    }
}
