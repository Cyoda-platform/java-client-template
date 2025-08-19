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
public class EnrichFareProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichFareProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EnrichFareProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing EnrichFareProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FlightOption.class)
            .validate(this::isValidEntity, "Invalid entity state for fare enrichment")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FlightOption entity) {
        return entity != null && entity.getStatus() != null && "CREATED".equalsIgnoreCase(entity.getStatus());
    }

    private FlightOption processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FlightOption> context) {
        FlightOption entity = context.entity();
        try {
            String idForLog = entity.getOptionId() != null ? entity.getOptionId() : "<unknown>";
            if (entity.getFareRules() == null || entity.getFareRules().isEmpty()) {
                logger.debug("Setting status -> ENRICHING for option {}", idForLog);
                entity.setStatus("ENRICHING");

                // If an external fare provider is configured, attempt to fetch fare details
                String fareProvider = System.getenv("FARE_PROVIDER_URL");
                if (fareProvider != null && !fareProvider.isEmpty()) {
                    String query = String.format("?airline=%s&flightNumber=%s",
                        urlEncode(entity.getAirline()), urlEncode(entity.getFlightNumber()));
                    String fullUrl = fareProvider + query;
                    logger.debug("Querying fare provider: {}", fullUrl);

                    // Simple retry loop for transient failures
                    int attempts = 0;
                    Exception lastEx = null;
                    while (attempts < 2) {
                        attempts++;
                        try {
                            URL url = new URL(fullUrl);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setConnectTimeout(3000);
                            conn.setReadTimeout(5000);

                            int code = conn.getResponseCode();
                            try (Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
                                StringBuilder sb = new StringBuilder();
                                while (s.hasNextLine()) sb.append(s.nextLine());
                                String resp = sb.toString();
                                if (code >= 200 && code < 300) {
                                    // Parse provider response to extract fareRules summary
                                    try {
                                        JsonNode root = objectMapper.readTree(resp);
                                        StringBuilder summary = new StringBuilder();
                                        if (root.has("fareRulesSummary")) {
                                            summary.append(root.get("fareRulesSummary").asText());
                                        } else if (root.has("fares") && root.get("fares").isArray() && root.get("fares").size() > 0) {
                                            JsonNode fare = root.get("fares").get(0);
                                            if (fare.has("refundable")) summary.append("refundable=").append(fare.get("refundable").asText()).append(",");
                                            if (fare.has("changeFee")) summary.append("change_fee=").append(fare.get("changeFee").asText());
                                        } else {
                                            summary.append("Fare details unavailable from provider");
                                        }
                                        entity.setFareRules(summary.toString());
                                        return entity;
                                    } catch (Exception pe) {
                                        logger.warn("Failed to parse fare provider response for {}: {}", idForLog, pe.getMessage());
                                        lastEx = pe;
                                    }
                                } else {
                                    logger.warn("Fare provider returned http {} for {}", code, idForLog);
                                    lastEx = new RuntimeException("Fare provider http " + code);
                                }
                            }
                        } catch (Exception ex) {
                            lastEx = ex;
                            logger.warn("Transient error contacting fare provider for {}: {}", idForLog, ex.getMessage());
                            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                        }
                    }
                    // If we reach here, external enrichment failed. Fall back to default summary but do not fail the workflow.
                    logger.debug("External fare enrichment failed for {}: {}", idForLog, lastEx != null ? lastEx.getMessage() : "unknown");
                    entity.setFareRules("Fare rules summary: provider lookup failed or unavailable");
                    return entity;
                } else {
                    // No external provider configured; create a reasonable default summary
                    String summary = "Fare rules summary: refundable=false, change_fee=200";
                    entity.setFareRules(summary);
                    return entity;
                }
            }
            return entity;
        } catch (Exception ex) {
            logger.error("Error enriching fare for option {}", entity != null ? entity.getOptionId() : "<null>", ex);
            if (entity != null) {
                entity.setStatus("ERROR");
                entity.setSeatAvailability(entity.getSeatAvailability()); // preserve existing
            }
            return entity;
        }
    }

    private String urlEncode(String v) {
        try { return java.net.URLEncoder.encode(v == null ? "" : v, java.nio.charset.StandardCharsets.UTF_8.name()); } catch (Exception e) { return v; }
    }
}
