package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public StartIngestionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Initialize job ingestion state using reflection to avoid relying on generated accessor methods
        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        setField(job, "state", "INGESTING");
        setField(job, "startedAt", now);
        setField(job, "finishedAt", null);
        setField(job, "recordsFetchedCount", Integer.valueOf(0));
        setField(job, "recordsProcessedCount", Integer.valueOf(0));
        setField(job, "recordsFailedCount", Integer.valueOf(0));
        setField(job, "errorSummary", null);

        HttpClient httpClient = HttpClient.newHttpClient();
        // fetch a reasonable number of records; can be adjusted
        String apiUrl = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records?limit=100";

        List<Laureate> laureatesToPersist = new ArrayList<>();
        int fetchedCount = 0;
        int mappingFailures = 0;
        StringBuilder errors = new StringBuilder();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status != 200) {
                String err = "Failed to fetch records, HTTP status: " + status;
                logger.error(err);
                setField(job, "state", "FAILED");
                setField(job, "finishedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                setField(job, "errorSummary", err);
                return job;
            }

            String body = response.body();
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
            com.fasterxml.jackson.databind.JsonNode recordsNode = root.path("records");
            if (!recordsNode.isArray()) {
                // try fallback to 'records' being top-level or single object
                logger.warn("Unexpected JSON structure: 'records' is not an array");
                setField(job, "state", "FAILED");
                setField(job, "finishedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                setField(job, "errorSummary", "Unexpected API response structure");
                return job;
            }

            fetchedCount = recordsNode.size();

            for (com.fasterxml.jackson.databind.JsonNode rec : recordsNode) {
                try {
                    com.fasterxml.jackson.databind.JsonNode fields = rec.has("fields") ? rec.get("fields") : rec;

                    Laureate l = new Laureate();

                    // id (expected integer)
                    Integer idVal = null;
                    if (fields.has("id") && !fields.get("id").isNull()) {
                        try {
                            idVal = fields.get("id").isInt() ? Integer.valueOf(fields.get("id").asInt()) : (fields.get("id").canConvertToInt() ? Integer.valueOf(fields.get("id").asInt()) : null);
                        } catch (Exception e) {
                            logger.debug("Unable to parse id for record: {}", e.getMessage());
                        }
                    }
                    if (idVal == null && fields.has("laureateid") && !fields.get("laureateid").isNull()) {
                        try {
                            idVal = Integer.valueOf(fields.get("laureateid").asInt());
                        } catch (Exception ignored) {}
                    }
                    if (idVal != null) setField(l, "id", idVal);

                    // Basic personal details
                    setField(l, "firstname", getTextOrNull(fields, "firstname", "firstname_en", "firstname_en"));
                    setField(l, "surname", getTextOrNull(fields, "surname", "surname_en", "familyname"));

                    // Dates and origin
                    setField(l, "born", getTextOrNull(fields, "born"));
                    setField(l, "died", getTextOrNull(fields, "died"));
                    setField(l, "bornCountry", getTextOrNull(fields, "borncountry", "born_country"));
                    setField(l, "bornCountryCode", getTextOrNull(fields, "borncountrycode", "born_country_code", "borncountrycode"));
                    setField(l, "bornCity", getTextOrNull(fields, "borncity", "born_city"));

                    // Award info
                    setField(l, "year", getTextOrNull(fields, "year"));
                    setField(l, "category", getTextOrNull(fields, "category"));
                    setField(l, "motivation", getTextOrNull(fields, "motivation"));

                    // Affiliation info - dataset example uses name/city/country for affiliation
                    setField(l, "affiliationName", getTextOrNull(fields, "name", "affiliation_name", "org_name"));
                    setField(l, "affiliationCity", getTextOrNull(fields, "city", "affiliation_city"));
                    setField(l, "affiliationCountry", getTextOrNull(fields, "country", "affiliation_country"));

                    setField(l, "gender", getTextOrNull(fields, "gender"));

                    // initial enrichment/status fields
                    setField(l, "validationStatus", "PENDING");
                    setField(l, "lastSeenAt", now);

                    // Basic sanity: require id, firstname and surname per entity validation; if absent treat as mapping failure
                    Object lId = getFieldValue(l, "id");
                    String lFirstname = (String) getFieldValue(l, "firstname");
                    String lSurname = (String) getFieldValue(l, "surname");

                    if (lId == null || lFirstname == null || lSurname == null) {
                        mappingFailures++;
                        String msg = "Missing required fields (id/firstname/surname) for record; skipping";
                        errors.append(msg).append("; ");
                        logger.warn(msg);
                        continue;
                    }

                    laureatesToPersist.add(l);
                } catch (Exception ex) {
                    mappingFailures++;
                    String msg = "Error mapping record: " + ex.getMessage();
                    errors.append(msg).append("; ");
                    logger.error(msg, ex);
                }
            }

            // Persist laureates (if any)
            int processed = 0;
            if (!laureatesToPersist.isEmpty()) {
                try {
                    CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                        Laureate.ENTITY_NAME,
                        Laureate.ENTITY_VERSION,
                        laureatesToPersist
                    );
                    List<UUID> ids = idsFuture.get();
                    if (ids != null) processed = ids.size();
                } catch (Exception e) {
                    // treat as fatal error for ingestion
                    String err = "Failed to persist laureates: " + e.getMessage();
                    logger.error(err, e);
                    setField(job, "state", "FAILED");
                    setField(job, "finishedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                    setField(job, "errorSummary", err);
                    // set counts before returning
                    setField(job, "recordsFetchedCount", Integer.valueOf(fetchedCount));
                    setField(job, "recordsProcessedCount", Integer.valueOf(0));
                    setField(job, "recordsFailedCount", Integer.valueOf(mappingFailures + laureatesToPersist.size()));
                    return job;
                }
            }

            // finalize job state: if fatal not hit then SUCCEEDED
            setField(job, "state", "SUCCEEDED");
            setField(job, "finishedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            setField(job, "recordsFetchedCount", Integer.valueOf(fetchedCount));
            setField(job, "recordsProcessedCount", Integer.valueOf(processed));
            setField(job, "recordsFailedCount", Integer.valueOf(mappingFailures + (laureatesToPersist.size() - processed)));

            if (errors.length() > 0) {
                setField(job, "errorSummary", errors.toString());
            } else {
                setField(job, "errorSummary", null);
            }

            return job;

        } catch (Exception e) {
            logger.error("Unexpected error during ingestion", e);
            setField(job, "state", "FAILED");
            setField(job, "finishedAt", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            String err = "Unexpected ingestion error: " + e.getMessage();
            setField(job, "errorSummary", err);
            setField(job, "recordsFetchedCount", Integer.valueOf(fetchedCount));
            setField(job, "recordsProcessedCount", Integer.valueOf(0));
            setField(job, "recordsFailedCount", Integer.valueOf(fetchedCount)); // assume all failed
            return job;
        }
    }

    // Helper to pick first present text field from candidates or null
    private String getTextOrNull(com.fasterxml.jackson.databind.JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) {
                String v = node.get(k).asText();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    // Reflection helpers to set/get private fields to avoid reliance on generated Lombok accessors in environments where annotation processing may not be active.
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), fieldName);
            if (f == null) {
                logger.debug("Field '{}' not found on {}", fieldName, target.getClass().getName());
                return;
            }
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            logger.warn("Failed to set field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
        }
    }

    private Object getFieldValue(Object target, String fieldName) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), fieldName);
            if (f == null) {
                logger.debug("Field '{}' not found on {}", fieldName, target.getClass().getName());
                return null;
            }
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            logger.warn("Failed to get field '{}' on {}: {}", fieldName, target.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        Class<?> cur = clazz;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}