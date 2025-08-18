package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

@Component
public class FetchAndTransformProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchAndTransformProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public FetchAndTransformProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchAndTransform for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job for fetch/transform")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getSourceUrl() != null && !job.getSourceUrl().isBlank();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        try {
            String source = job.getSourceUrl();
            if (source == null || source.isBlank()) return job;

            int maxPages = 10; // safeguard
            String pageToken = null;
            int fetched = 0;
            List<Laureate> transformed = new ArrayList<>();

            for (int p = 0; p < maxPages; p++) {
                try {
                    // Build URL with pagination token if present
                    String urlStr = source + (pageToken == null ? "" : (source.contains("?") ? "&" : "?") + "page=" + pageToken);
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(10000);
                    int status = conn.getResponseCode();
                    InputStream in = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
                    try (Scanner s = new Scanner(in).useDelimiter("\\A")) {
                        String body = s.hasNext() ? s.next() : "";
                        JsonNode root = objectMapper.readTree(body);
                        ArrayNode records = root.has("records") ? (ArrayNode) root.get("records") : null;
                        if (records == null || records.isEmpty()) break;
                        for (JsonNode r : records) {
                            try {
                                Laureate l = new Laureate();
                                // Map fields defensively
                                JsonNode fields = r.has("fields") ? r.get("fields") : r;
                                l.setFullName(fields.has("fullName") ? fields.get("fullName").asText() : fields.has("fullname") ? fields.get("fullname").asText() : "");
                                l.setYear(fields.has("year") ? fields.get("year").asText() : fields.has("awardYear") ? fields.get("awardYear").asText() : "");
                                l.setCategory(fields.has("category") ? fields.get("category").asText() : "");
                                l.setAffiliation(fields.has("affiliation") ? fields.get("affiliation").asText() : null);
                                l.setCountry(fields.has("country") ? fields.get("country").asText() : null);
                                l.setSourceUrl(urlStr);
                                l.setCreatedAt(Instant.now().toString());
                                l.setChangeType("NEW");
                                transformed.add(l);
                                fetched++;

                                // Persist transformed record to staging via PersistLaureateEventsProcessor (call entityService.addItem for Laureate)
                                try {
                                    CompletableFuture<java.util.UUID> fut = entityService.addItem(Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION), l);
                                    fut.whenComplete((u, ex) -> {
                                        if (ex != null) logger.warn("Failed to add staging laureate: {}", ex.getMessage());
                                    });
                                } catch (Exception ee) {
                                    logger.warn("Error adding laureate to staging: {}", ee.getMessage());
                                }

                            } catch (Exception e) {
                                logger.warn("Error transforming record in job {}: {}", job.getTechnicalId(), e.getMessage());
                            }
                        }
                        // pagination token extraction
                        if (root.has("next")) {
                            pageToken = root.get("next").asText();
                            if (pageToken == null || pageToken.isBlank()) break;
                        } else {
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error fetching page {} for job {}: {}", p, job.getTechnicalId(), e.getMessage());
                    break;
                }
            }
            logger.info("Job {} fetched {} records", job.getTechnicalId(), fetched);
        } catch (Exception e) {
            logger.error("Unexpected error during fetch/transform for job {}: {}", job.getTechnicalId(), e.getMessage());
        }
        return job;
    }
}
