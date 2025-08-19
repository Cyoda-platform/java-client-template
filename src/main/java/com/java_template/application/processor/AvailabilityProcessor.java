package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.flightoption.version_1.FlightOption;
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

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

@Component
public class AvailabilityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AvailabilityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AvailabilityProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AvailabilityProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FlightOption.class)
            .validate(this::isValidEntity, "Invalid entity state for availability check")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FlightOption entity) {
        return entity != null && entity.getStatus() != null && ("CREATED".equalsIgnoreCase(entity.getStatus()) || "ENRICHING".equalsIgnoreCase(entity.getStatus()) || "AVAILABILITY_CHECK".equalsIgnoreCase(entity.getStatus()));
    }

    private FlightOption processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FlightOption> context) {
        FlightOption entity = context.entity();
        try {
            String idForLog = entity.getOptionId() != null ? entity.getOptionId() : "<unknown>";
            logger.debug("Setting status -> AVAILABILITY_CHECK for option {}", idForLog);
            entity.setStatus("AVAILABILITY_CHECK");

            // If an external seat provider is configured, call it to determine seat availability.
            String seatProvider = System.getenv("SEAT_PROVIDER_URL");
            if (seatProvider != null && !seatProvider.isEmpty()) {
                String query = String.format("?airline=%s&flightNumber=%s&optionId=%s",
                    urlEncode(entity.getAirline()), urlEncode(entity.getFlightNumber()), urlEncode(entity.getOptionId()));
                String fullUrl = seatProvider + query;
                logger.debug("Contacting seat provider: {}", fullUrl);

                int attempts = 0;
                Exception lastEx = null;
                while (attempts < 3) {
                    attempts++;
                    try {
                        URL url = new URL(fullUrl);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(5000);

                        int code = conn.getResponseCode();
                        try (Scanner s = new Scanner(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8.name())) {
                            StringBuilder sb = new StringBuilder();
                            while (s.hasNextLine()) sb.append(s.nextLine());
                            String resp = sb.toString();

                            if (code >= 200 && code < 300) {
                                try {
                                    JsonNode root = objectMapper.readTree(resp);
                                    Integer seats = null;
                                    if (root.has("seatAvailability") && !root.get("seatAvailability").isNull()) {
                                        seats = root.get("seatAvailability").asInt();
                                    } else if (root.has("seats") && !root.get("seats").isNull()) {
                                        seats = root.get("seats").asInt();
                                    }
                                    if (seats == null) {
                                        logger.debug("Seat provider response did not include seats for {}", idForLog);
                                        entity.setSeatAvailability(null);
                                        // Mark unavailable as unknown -> treat as no seats
                                        entity.setStatus("UNAVAILABLE");
                                        return entity;
                                    }
                                    entity.setSeatAvailability(seats);
                                    if (seats > 0) entity.setStatus("READY"); else entity.setStatus("UNAVAILABLE");
                                    return entity;
                                } catch (Exception pe) {
                                    lastEx = pe;
                                    logger.warn("Failed to parse seat provider response for {}: {}", idForLog, pe.getMessage());
                                }
                            } else {
                                lastEx = new RuntimeException("Seat provider returned http " + code + ": " + resp);
                                logger.warn("Seat provider returned non-2xx for {}: {}", idForLog, code);
                            }
                        }
                    } catch (Exception ex) {
                        lastEx = ex;
                        logger.warn("Transient error contacting seat provider for {}: {}", idForLog, ex.getMessage());
                        try { Thread.sleep(200L * attempts); } catch (InterruptedException ignored) {}
                    }
                }
                // If we exhausted retries without success, mark option error but avoid throwing
                logger.error("Seat provider lookup failed after retries for {}: {}", idForLog, lastEx != null ? lastEx.getMessage() : "unknown");
                entity.setSeatAvailability(null);
                entity.setStatus("ERROR");
                entity.setErrorMessage("Seat provider lookup failed: " + (lastEx != null ? lastEx.getMessage() : "unknown"));
                return entity;
            }

            // No external provider configured: use existing seatAvailability if present, otherwise simulate 2 seats
            Integer seats = entity.getSeatAvailability();
            if (seats == null) seats = 2;
            entity.setSeatAvailability(seats);
            if (seats > 0) entity.setStatus("READY"); else entity.setStatus("UNAVAILABLE");
            return entity;
        } catch (Exception ex) {
            logger.error("Error checking availability for option {}", entity != null ? entity.getOptionId() : "<null>", ex);
            if (entity != null) {
                entity.setStatus("ERROR");
                entity.setErrorMessage("Availability check failed: " + ex.getMessage());
            }
            return entity;
        }
    }

    private String urlEncode(String v) {
        try { return java.net.URLEncoder.encode(v == null ? "" : v, java.nio.charset.StandardCharsets.UTF_8.name()); } catch (Exception e) { return v; }
    }
}
