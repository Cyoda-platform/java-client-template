package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
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

import java.time.Instant;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

@Component
public class ArchiveStaleFactsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveStaleFactsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final long STALE_DAYS = 365; // archive facts older than 1 year by default

    public ArchiveStaleFactsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ArchiveStaleFacts for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CatFact entity) {
        return entity != null && entity.isValid();
    }

    private CatFact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFact> context) {
        CatFact fact = context.entity();
        try {
            String fetchedAt = fact.getFetchedAt();
            if (fetchedAt == null) {
                return fact;
            }
            try {
                Instant fetched = Instant.parse(fetchedAt);
                Instant now = Instant.now();
                long days = Duration.between(fetched, now).toDays();
                if (days > STALE_DAYS) {
                    fact.setStatus("archived");
                    logger.info("CatFact {} archived due to staleness ({} days)", fact.getId(), days);
                }
            } catch (DateTimeParseException dte) {
                logger.warn("Could not parse fetchedAt for CatFact {}: {}", fact.getId(), fetchedAt);
            }
        } catch (Exception ex) {
            logger.error("Error archiving stale CatFact {}: {}", fact.getId(), ex.getMessage(), ex);
        }
        return fact;
    }
}
