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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class EnrichBookingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichBookingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EnrichBookingProcessor(SerializerFactory serializerFactory) {
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

        if (entity == null) return null;

        try {
            // Normalize and trim names
            if (entity.getFirstname() != null) {
                String fn = entity.getFirstname().trim();
                if (!fn.isBlank()) {
                    entity.setFirstname(capitalizeWords(fn));
                }
            }
            if (entity.getLastname() != null) {
                String ln = entity.getLastname().trim();
                if (!ln.isBlank()) {
                    entity.setLastname(capitalizeWords(ln));
                }
            }

            // Normalize source
            if (entity.getSource() == null || entity.getSource().isBlank()) {
                entity.setSource("RestfulBooker");
            } else {
                entity.setSource(entity.getSource().trim());
            }

            // Ensure persistedAt is present (ISO instant)
            if (entity.getPersistedAt() == null || entity.getPersistedAt().isBlank()) {
                entity.setPersistedAt(Instant.now().toString());
            }

            // Normalize checkin/checkout to ISO_LOCAL_DATE if possible
            DateTimeFormatter isoDate = DateTimeFormatter.ISO_LOCAL_DATE;
            if (entity.getCheckin() != null && !entity.getCheckin().isBlank()) {
                try {
                    LocalDate d = LocalDate.parse(entity.getCheckin());
                    entity.setCheckin(d.format(isoDate));
                } catch (Exception ex) {
                    // leave as-is but log; invalid dates should be caught earlier in validation
                    logger.warn("Unable to normalize checkin date '{}' for bookingId {}: {}", entity.getCheckin(), entity.getBookingId(), ex.getMessage());
                }
            }
            if (entity.getCheckout() != null && !entity.getCheckout().isBlank()) {
                try {
                    LocalDate d = LocalDate.parse(entity.getCheckout());
                    entity.setCheckout(d.format(isoDate));
                } catch (Exception ex) {
                    logger.warn("Unable to normalize checkout date '{}' for bookingId {}: {}", entity.getCheckout(), entity.getBookingId(), ex.getMessage());
                }
            }

            // Ensure totalprice has two decimal precision
            if (entity.getTotalprice() != null) {
                BigDecimal bd = BigDecimal.valueOf(entity.getTotalprice()).setScale(2, RoundingMode.HALF_UP);
                entity.setTotalprice(bd.doubleValue());
            }

            // Ensure depositpaid is not null (booking.isValid should have enforced this,
            // but as enrichment we avoid changing required semantics; only log if null)
            if (entity.getDepositpaid() == null) {
                logger.warn("Booking depositpaid is null for bookingId {}; leaving unchanged to avoid altering validation semantics.", entity.getBookingId());
            }

            // No persistence calls for this triggering entity — Cyoda will persist updated entity state automatically.
            logger.debug("Enriched booking {}: firstname='{}', lastname='{}', totalprice={}, persistedAt={}, source={}",
                    entity.getBookingId(),
                    entity.getFirstname(),
                    entity.getLastname(),
                    entity.getTotalprice(),
                    entity.getPersistedAt(),
                    entity.getSource());

        } catch (Exception e) {
            logger.error("Error enriching booking {}: {}", entity.getBookingId(), e.getMessage(), e);
            // Do not throw — return entity as-is. Errors here should not crash pipeline.
        }

        return entity;
    }

    private String capitalizeWords(String input) {
        if (input == null || input.isBlank()) return input;
        String[] parts = input.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.length() == 0) continue;
            String cap = p.substring(0, 1).toUpperCase(Locale.ROOT) + (p.length() > 1 ? p.substring(1) : "");
            if (i > 0) sb.append(' ');
            sb.append(cap);
        }
        return sb.toString();
    }
}