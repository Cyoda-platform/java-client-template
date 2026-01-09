package com.example.application.processor;

import com.example.application.entity.transaction.version_1.Transaction;
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

import java.util.HashMap;
import java.util.Map;

/**
 * FuzzyWatchlistMatch Processor - Transaction Monitoring Workflow
 * 
 * Performs fuzzy matching against watchlists (OFAC, PEP, etc.).
 * Identifies potential matches with sanctioned entities and high-risk individuals.
 */
@Component
public class FuzzyWatchlistMatch implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FuzzyWatchlistMatch.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FuzzyWatchlistMatch(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Performing watchlist matching for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Transaction.class)
                .validate(this::isValidEntityWithMetadata, "Invalid transaction entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Transaction> entityWithMetadata) {
        Transaction entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<Transaction> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Transaction> context) {

        EntityWithMetadata<Transaction> entityWithMetadata = context.entityResponse();
        Transaction transaction = entityWithMetadata.entity();

        logger.debug("Matching transaction {} against watchlists", transaction.getId());

        // Perform fuzzy matching against watchlists
        performWatchlistMatching(transaction);

        // Update metadata
        if (transaction.getMetadata() == null) {
            transaction.setMetadata(new HashMap<>());
        }
        transaction.getMetadata().put("watchlistChecked", true);
        transaction.getMetadata().put("watchlistCheckTime", System.currentTimeMillis());

        logger.info("Watchlist matching completed for transaction: {}", transaction.getId());
        return entityWithMetadata;
    }

    private void performWatchlistMatching(Transaction transaction) {
        // Simulate watchlist matching against multiple sources
        
        // Check OFAC SDN list
        checkOFACList(transaction);
        
        // Check PEP (Politically Exposed Persons) list
        checkPEPList(transaction);
        
        // Check other sanctions lists
        checkOtherSanctionsList(transaction);
    }

    private void checkOFACList(Transaction transaction) {
        // Simulate OFAC matching
        // In production, this would call actual OFAC database
        if (transaction.getMerchant() != null && transaction.getMerchant().toLowerCase().contains("sanctioned")) {
            transaction.getMatchedWatchlistIds().add("OFAC-SDN");
            transaction.getFlags().add("OFAC_MATCH");
            logger.warn("OFAC match found for transaction: {}", transaction.getId());
        }
    }

    private void checkPEPList(Transaction transaction) {
        // Simulate PEP matching
        if (transaction.getCountry() != null && transaction.getCountry().equals("KP")) {
            transaction.getMatchedWatchlistIds().add("PEP-NORTH_KOREA");
            transaction.getFlags().add("PEP_MATCH");
            logger.warn("PEP match found for transaction: {}", transaction.getId());
        }
    }

    private void checkOtherSanctionsList(Transaction transaction) {
        // Simulate other sanctions list matching
        if (transaction.getCountry() != null) {
            String country = transaction.getCountry().toUpperCase();
            if (country.equals("IR") || country.equals("SY") || country.equals("CU")) {
                transaction.getMatchedWatchlistIds().add("SANCTIONS-" + country);
                transaction.getFlags().add("SANCTIONS_MATCH");
                logger.warn("Sanctions match found for transaction: {}", transaction.getId());
            }
        }
    }
}

