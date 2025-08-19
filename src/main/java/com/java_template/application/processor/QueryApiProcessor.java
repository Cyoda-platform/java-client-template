package com.java_template.application.processor;

import com.java_template.application.entity.flightsearch.version_1.FlightSearch;
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
public class QueryApiProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QueryApiProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public QueryApiProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing QueryApiProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FlightSearch.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FlightSearch entity) {
        return entity != null && entity.getStatus() != null && "VALIDATING".equalsIgnoreCase(entity.getStatus());
    }

    private FlightSearch processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FlightSearch> context) {
        FlightSearch entity = context.entity();
        try {
            String sid = entity.getSearchId() != null ? entity.getSearchId() : "<unknown>";
            logger.debug("Setting status -> QUERYING for search {}", sid);
            entity.setStatus("QUERYING");

            // For prototype: if a provider URL is configured, attempt a GET to observe availability.
            // We do not persist the raw provider response because the FlightSearch entity does not contain a rawResponse field in the current model.
            String providerUrl = System.getenv("FLIGHT_PROVIDER_URL");
            if (providerUrl == null || providerUrl.isEmpty()) {
                // No external provider configured; proceed and allow mapping to generate simulated options
                return entity;
            }

            String query = String.format("?origin=%s&destination=%s&departure=%s&passengers=%d",
                urlEncode(entity.getOriginAirportCode()),
                urlEncode(entity.getDestinationAirportCode() == null ? "" : entity.getDestinationAirportCode()),
                urlEncode(entity.getDepartureDate()),
                entity.getPassengerCount() == null ? 1 : entity.getPassengerCount()
            );

            URL url = new URL(providerUrl + query);
            logger.debug("Querying external flights provider: {}", url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                // We intentionally do not store the raw response on the FlightSearch entity (field not present). Mapping will simulate options.
                try (Scanner s = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
                    StringBuilder sb = new StringBuilder();
                    while (s.hasNextLine()) sb.append(s.nextLine());
                    logger.debug("Provider response length: {}", sb.length());
                    return entity;
                }
            } else {
                try (Scanner s = new Scanner(conn.getErrorStream() == null ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8.name())) {
                    StringBuilder sb = new StringBuilder();
                    while (s.hasNextLine()) sb.append(s.nextLine());
                    String resp = sb.toString();
                    entity.setStatus("ERROR");
                    entity.setErrorMessage("Provider returned http " + code + ": " + (resp.length() > 200 ? resp.substring(0,200) : resp));
                    return entity;
                }
            }
        } catch (Exception ex) {
            logger.error("Error querying provider for search {}", entity.getSearchId(), ex);
            entity.setStatus("ERROR");
            entity.setErrorMessage("Error querying provider: " + ex.getMessage());
            return entity;
        }
    }

    private String urlEncode(String v) {
        try { return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8.name()); } catch (Exception e) { return v; }
    }
}
