package com.java_template.application.processor;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class JobExecutionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobExecutionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public JobExecutionProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business logic:
        // 1. Fetch data from job.sourceEndpoint (expected JSON array of records)
        // 2. For each record map to Laureate entity and persist via entityService.addItem
        // 3. Update job.resultSummary, lastRunAt, status accordingly
        String source = tryGetString(job, "getSourceEndpoint", "getSource", "getSourceUrl");
        Map<String, Object> params = tryGetObject(job, Map.class, "getParameters", "getParams", "getParametersMap");

        int processed = 0;
        int created = 0;
        int failed = 0;
        List<CompletableFuture<UUID>> futures = new ArrayList<>();

        String jobIdForLog = tryGetString(job, "getJobId", "getId", "getJobIdentifier");
        if (jobIdForLog == null) jobIdForLog = "(unknown)";

        if (source == null || source.isBlank()) {
            logger.error("Job {} has empty sourceEndpoint", jobIdForLog);
            trySet(job, "setStatus", String.class, "FAILED");
            Integer rc = tryGetObject(job, Integer.class, "getRetryCount", "getRetries");
            trySet(job, "setRetryCount", Integer.class, rc == null ? 1 : rc + 1);
            trySet(job, "setLastRunAt", String.class, Instant.now().toString());
            trySet(job, "setResultSummary", String.class, "no source endpoint");
            return job;
        }

        try {
            // Build URL with simple query params if provided (parameters are optional)
            String url = source;
            if (params != null && !params.isEmpty()) {
                StringBuilder sb = new StringBuilder(url);
                boolean first = !url.contains("?");
                if (params.containsKey("query")) {
                    sb.append(first ? "?" : "&").append("q=").append(params.get("query"));
                    first = false;
                }
                if (params.containsKey("limit")) {
                    sb.append(first ? "?" : "&").append("limit=").append(params.get("limit"));
                }
                url = sb.toString();
            }

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = httpResponse.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                logger.error("Failed to fetch source for job {}: HTTP {}", jobIdForLog, statusCode);
                trySet(job, "setStatus", String.class, "FAILED");
                Integer rc = tryGetObject(job, Integer.class, "getRetryCount", "getRetries");
                trySet(job, "setRetryCount", Integer.class, rc == null ? 1 : rc + 1);
                trySet(job, "setLastRunAt", String.class, Instant.now().toString());
                trySet(job, "setResultSummary", String.class, "fetch failed: HTTP " + statusCode);
                return job;
            }

            String body = httpResponse.body();
            JsonNode root = objectMapper.readTree(body);

            // Support both array root and object with "records" field
            JsonNode recordsNode = root.isArray() ? root : root.path("records");
            if (recordsNode == null || !recordsNode.isArray()) {
                logger.warn("Source returned no array records for job {}.", jobIdForLog);
                trySet(job, "setStatus", String.class, "COMPLETED");
                trySet(job, "setLastRunAt", String.class, Instant.now().toString());
                trySet(job, "setResultSummary", String.class, "no records");
                return job;
            }

            for (JsonNode record : recordsNode) {
                processed++;
                try {
                    Laureate laureate = mapRecordToLaureate(record);
                    // Persist laureate as a new entity to trigger Laureate workflow
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        laureate
                    );
                    futures.add(idFuture);
                } catch (Exception e) {
                    failed++;
                    logger.error("Failed to map/persist record for job {}: {}", jobIdForLog, e.getMessage(), e);
                }
            }

            // Await addItem completions and count successes/failures
            for (CompletableFuture<UUID> f : futures) {
                try {
                    UUID id = f.join();
                    if (id != null) created++;
                    else failed++;
                } catch (Exception e) {
                    failed++;
                    logger.error("Error while waiting for Laureate addItem completion: {}", e.getMessage(), e);
                }
            }

            // Compose result summary
            String summary = String.format("processed %d, created %d, failed %d", processed, created, failed);
            trySet(job, "setResultSummary", String.class, summary);
            trySet(job, "setLastRunAt", String.class, Instant.now().toString());
            trySet(job, "setStatus", String.class, failed == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");

            // reset retry count on success path
            trySet(job, "setRetryCount", Integer.class, 0);

        } catch (Exception ex) {
            logger.error("Exception executing job {}: {}", jobIdForLog, ex.getMessage(), ex);
            trySet(job, "setStatus", String.class, "FAILED");
            Integer rc = tryGetObject(job, Integer.class, "getRetryCount", "getRetries");
            trySet(job, "setRetryCount", Integer.class, rc == null ? 1 : rc + 1);
            trySet(job, "setLastRunAt", String.class, Instant.now().toString());
            trySet(job, "setResultSummary", String.class, "error: " + ex.getMessage());
        }

        return job;
    }

    private Laureate mapRecordToLaureate(JsonNode record) {
        Laureate l = new Laureate();

        String laureateId = textOrNull(record.path("laureateId"));
        if (laureateId == null) laureateId = textOrNull(record.path("id"));
        trySet(l, "setLaureateId", String.class, laureateId);

        String fullName = textOrNull(record.path("fullName"));
        if (fullName == null) fullName = textOrNull(record.path("name"));
        trySet(l, "setFullName", String.class, fullName);

        if (record.has("prizeYear") && record.get("prizeYear").canConvertToInt()) {
            trySet(l, "setPrizeYear", Integer.class, record.get("prizeYear").asInt());
        } else if (record.has("year") && record.get("year").canConvertToInt()) {
            trySet(l, "setPrizeYear", Integer.class, record.get("year").asInt());
        }

        trySet(l, "setCategory", String.class, textOrNull(record.path("category")));
        trySet(l, "setCountry", String.class, textOrNull(record.path("country")));

        // affiliations array
        if (record.has("affiliations") && record.get("affiliations").isArray()) {
            List<String> aff = new ArrayList<>();
            for (JsonNode a : record.get("affiliations")) {
                if (!a.isNull()) {
                    String val = textOrNull(a);
                    if (val != null) aff.add(val);
                }
            }
            trySet(l, "setAffiliations", List.class, aff);
        }

        String changeType = textOrNull(record.path("changeType"));
        trySet(l, "setChangeType", String.class, changeType != null ? changeType : "new");
        trySet(l, "setRawPayload", String.class, record.toString());
        trySet(l, "setDetectedAt", String.class, Instant.now().toString());
        trySet(l, "setPublished", Boolean.class, Boolean.FALSE);

        return l;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String t = node.asText();
        return (t == null || t.isBlank()) ? null : t;
    }

    // Reflection helpers to avoid compile-time dependency on specific getter/setter names.
    private Object invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object[] args) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName, paramTypes);
            return m.invoke(target, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String tryGetString(Object target, String... methodNames) {
        Object val = tryGetObject(target, String.class, methodNames);
        return val == null ? null : val.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T tryGetObject(Object target, Class<T> expectedType, String... methodNames) {
        if (target == null) return null;
        for (String name : methodNames) {
            Object res = invokeMethod(target, name, new Class<?>[0], new Object[0]);
            if (res != null) {
                if (expectedType.isInstance(res)) return (T) res;
                // attempt simple conversions
                if (expectedType == String.class) return (T) res.toString();
                if (expectedType == Integer.class && res instanceof Number) return (T) Integer.valueOf(((Number) res).intValue());
            }
        }
        // try direct field access as fallback
        for (String name : methodNames) {
            String fieldName = decapitalize(stripGetterSetterPrefix(name));
            try {
                Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object res = f.get(target);
                if (res != null && expectedType.isInstance(res)) return (T) res;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean trySet(Object target, String methodName, Class<?> paramType, Object value) {
        if (target == null) return false;
        try {
            Method m = target.getClass().getMethod(methodName, paramType);
            m.invoke(target, value);
            return true;
        } catch (Exception ignored) {
        }
        // try field fallback
        String fieldName = decapitalize(stripGetterSetterPrefix(methodName));
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private String stripGetterSetterPrefix(String name) {
        if (name.startsWith("get") || name.startsWith("set") || name.startsWith("is")) {
            if (name.startsWith("is")) return name.substring(2);
            return name.substring(3);
        }
        return name;
    }

    private String decapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.length() > 1 && Character.isUpperCase(s.charAt(1)) && Character.isUpperCase(s.charAt(0))) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}