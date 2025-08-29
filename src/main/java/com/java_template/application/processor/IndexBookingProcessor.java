package com.java_template.application.processor;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class IndexBookingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexBookingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IndexBookingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Booking for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Booking.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Booking entity) {
        return entity != null && entity.isValid();
    }

    private Booking processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Booking> context) {
        Booking entity = context.entity();

        // Ensure source is set (idempotent)
        try {
            if (entity.getSource() == null || entity.getSource().isBlank()) {
                entity.setSource("RestfulBooker");
                logger.debug("Set default source for bookingId={}: {}", entity.getBookingId(), entity.getSource());
            }
        } catch (Exception e) {
            logger.warn("Error while setting source for bookingId={}: {}", entity != null ? entity.getBookingId() : null, e.getMessage());
        }

        // Ensure persistedAt timestamp exists (idempotent)
        try {
            if (entity.getPersistedAt() == null || entity.getPersistedAt().isBlank()) {
                entity.setPersistedAt(Instant.now().toString());
                logger.debug("Set persistedAt for bookingId={}: {}", entity.getBookingId(), entity.getPersistedAt());
            }
        } catch (Exception e) {
            logger.warn("Error while setting persistedAt for bookingId={}: {}", entity != null ? entity.getBookingId() : null, e.getMessage());
        }

        // Normalize names (trim) - safe and idempotent
        try {
            if (entity.getFirstname() != null) {
                String trimmed = entity.getFirstname().trim();
                if (!trimmed.equals(entity.getFirstname())) {
                    entity.setFirstname(trimmed);
                }
            }
            if (entity.getLastname() != null) {
                String trimmed = entity.getLastname().trim();
                if (!trimmed.equals(entity.getLastname())) {
                    entity.setLastname(trimmed);
                }
            }
        } catch (Exception e) {
            logger.warn("Error while normalizing names for bookingId={}: {}", entity != null ? entity.getBookingId() : null, e.getMessage());
        }

        // Ensure depositpaid not null - default to false (idempotent)
        try {
            if (entity.getDepositpaid() == null) {
                entity.setDepositpaid(Boolean.FALSE);
            }
        } catch (Exception e) {
            logger.warn("Error while defaulting depositpaid for bookingId={}: {}", entity != null ? entity.getBookingId() : null, e.getMessage());
        }

        // Normalize totalprice: ensure non-negative and round to 2 decimals
        try {
            if (entity.getTotalprice() != null) {
                Double tp = entity.getTotalprice();
                if (tp < 0) {
                    logger.warn("Negative totalprice for bookingId={}. Setting to 0.0", entity.getBookingId());
                    entity.setTotalprice(0.0);
                } else {
                    BigDecimal bd = BigDecimal.valueOf(tp).setScale(2, RoundingMode.HALF_UP);
                    double rounded = bd.doubleValue();
                    if (Double.compare(rounded, tp) != 0) {
                        entity.setTotalprice(rounded);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error while normalizing totalprice for bookingId={}: {}", entity != null ? entity.getBookingId() : null, e.getMessage());
        }

        // Booking indexing is conceptually marking booking as READY for queries.
        // Since Booking entity does not have an explicit status field, ensure the entity
        // is normalized and persistedAt/source are present so downstream indexers/consumers can pick it up.

        logger.info("IndexBookingProcessor completed for bookingId={}", entity != null ? entity.getBookingId() : null);

        return entity;
    }
}