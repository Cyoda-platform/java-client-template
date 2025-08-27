package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StartIngestionProcessor(SerializerFactory serializerFactory,
                                   EntityService entityService,
                                   ObjectMapper objectMapper) {
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
        final Instant startedInstant = Instant.now();
        job.setStartedAt(DateTimeFormatter.ISO_INSTANT.format(startedInstant));
        job.setState("INGESTING");

        String source = job.getSourceEndpoint();
        if (source == null || source.isBlank()) {
            job.setErrorDetails("Missing sourceEndpoint");
            job.setFinishedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            job.setState("FAILED");
            job.setResultSummary("No sourceEndpoint provided");
            logger.error("Job {} failed due to missing sourceEndpoint", job.getJobId());
            return job;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        List<CompletableFuture<?>> addFutures = new ArrayList<>();
        int processedCount = 0;
        int errorCount = 0;
        StringBuilder errorDetailsBuilder = new StringBuilder();

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(source))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                String msg = "Failed to fetch source, HTTP status: " + status;
                job.setErrorDetails(msg);
                job.setFinishedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                job.setState("FAILED");
                job.setResultSummary(msg);
                logger.error("Job {} fetch failed with status {}", job.getJobId(), status);
                return job;
            }

            String body = response.body();
            JsonNode root = objectMapper.readTree(body);
            JsonNode recordsNode = root.path("records");
            if (recordsNode == null || recordsNode.isMissingNode() || !recordsNode.isArray()) {
                // fallback to root array or to possible "data" node
                if (root.isArray()) {
                    recordsNode = root;
                } else {
                    JsonNode dataNode = root.path("data");
                    if (dataNode != null && dataNode.isArray()) {
                        recordsNode = dataNode;
                    } else {
                        // treat single record response
                        recordsNode = objectMapper.createArrayNode().add(root);
                    }
                }
            }

            List<JsonNode> pages = new ArrayList<>();
            recordsNode.forEach(pages::add);

            for (JsonNode rec : pages) {
                try {
                    JsonNode fields = rec.has("fields") ? rec.path("fields") : rec;
                    if (fields == null || fields.isMissingNode()) fields = rec;

                    Laureate laureate = new Laureate();

                    // id - may be integer or string
                    if (fields.has("id") && fields.get("id").canConvertToInt()) {
                        laureate.setId(fields.get("id").asInt());
                    } else if (fields.has("id") && fields.get("id").isTextual()) {
                        try {
                            laureate.setId(Integer.valueOf(fields.get("id").asText()));
                        } catch (NumberFormatException ignored) {}
                    } else if (fields.has("laureate_id") && fields.get("laureate_id").canConvertToInt()) {
                        laureate.setId(fields.get("laureate_id").asInt());
                    }

                    // Strings
                    if (fields.has("firstname")) laureate.setFirstname(nullableText(fields.get("firstname")));
                    if (fields.has("firstname") == false && fields.has("given_name")) laureate.setFirstname(nullableText(fields.get("given_name")));
                    if (fields.has("surname")) laureate.setSurname(nullableText(fields.get("surname")));
                    if (fields.has("family_name") && (laureate.getSurname() == null || laureate.getSurname().isBlank()))
                        laureate.setSurname(nullableText(fields.get("family_name")));
                    if (fields.has("motivation")) laureate.setMotivation(nullableText(fields.get("motivation")));
                    if (fields.has("category")) laureate.setCategory(nullableText(fields.get("category")));
                    if (fields.has("year")) laureate.setYear(nullableText(fields.get("year")));
                    if (fields.has("born")) laureate.setBorn(nullableText(fields.get("born")));
                    if (fields.has("died")) laureate.setDied(nullableText(fields.get("died")));
                    if (fields.has("borncountry")) laureate.setBornCountry(nullableText(fields.get("borncountry")));
                    if (fields.has("borncountrycode")) laureate.setBornCountryCode(nullableText(fields.get("borncountrycode")));
                    if (fields.has("borncity")) laureate.setBornCity(nullableText(fields.get("borncity")));
                    if (fields.has("affiliation_name")) laureate.setAffiliationName(nullableText(fields.get("affiliation_name")));
                    if (fields.has("affiliation_city")) laureate.setAffiliationCity(nullableText(fields.get("affiliation_city")));
                    if (fields.has("affiliation_country")) laureate.setAffiliationCountry(nullableText(fields.get("affiliation_country")));
                    if (fields.has("gender")) laureate.setGender(nullableText(fields.get("gender")));
                    // some payloads use different keys for affiliation
                    if ((laureate.getAffiliationName() == null || laureate.getAffiliationName().isBlank()) && fields.has("name"))
                        laureate.setAffiliationName(nullableText(fields.get("name")));
                    if ((laureate.getAffiliationCity() == null || laureate.getAffiliationCity().isBlank()) && fields.has("city"))
                        laureate.setAffiliationCity(nullableText(fields.get("city")));
                    if ((laureate.getAffiliationCountry() == null || laureate.getAffiliationCountry().isBlank()) && fields.has("country"))
                        laureate.setAffiliationCountry(nullableText(fields.get("country")));

                    // derive ageAtAward if possible
                    try {
                        if (laureate.getBorn() != null && laureate.getYear() != null) {
                            String born = laureate.getBorn();
                            String birthYearStr = null;
                            if (born.length() >= 4) birthYearStr = born.substring(0, 4);
                            if (birthYearStr != null) {
                                int by = Integer.parseInt(birthYearStr);
                                int ay = Integer.parseInt(laureate.getYear());
                                laureate.setDerivedAgeAtAward(ay - by);
                            }
                        }
                    } catch (Exception ex) {
                        // ignore derived age errors
                    }

                    laureate.setPersistedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
                    // default status - will be classified by downstream processors
                    laureate.setRecordStatus("NEW");

                    // Add to entity store (do NOT update triggering Job via entityService)
                    CompletableFuture<?> addFuture = entityService.addItem(
                            Laureate.ENTITY_NAME,
                            String.valueOf(Laureate.ENTITY_VERSION),
                            laureate
                    );
                    addFutures.add(addFuture);
                    processedCount++;
                } catch (Exception inner) {
                    errorCount++;
                    String msg = "Record processing error: " + inner.getMessage();
                    errorDetailsBuilder.append(msg).append("; ");
                    logger.warn("Error processing a record for job {}: {}", job.getJobId(), inner.getMessage(), inner);
                }
            }

            // wait for all add operations to complete
            if (!addFutures.isEmpty()) {
                CompletableFuture<?>[] arr = addFutures.toArray(new CompletableFuture[0]);
                CompletableFuture.allOf(arr).join();
            }

            // finalize job
            job.setFinishedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            if (errorCount == 0) {
                job.setState("SUCCEEDED");
            } else {
                // partial errors considered succeeded unless zero processed
                if (processedCount > 0) {
                    job.setState("SUCCEEDED");
                } else {
                    job.setState("FAILED");
                }
            }
            job.setResultSummary(String.format("Processed=%d; Errors=%d", processedCount, errorCount));
            if (errorDetailsBuilder.length() > 0) job.setErrorDetails(errorDetailsBuilder.toString());
            logger.info("Job {} completed. {}", job.getJobId(), job.getResultSummary());

        } catch (Exception ex) {
            // fatal failure
            job.setFinishedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            job.setState("FAILED");
            String d = "Fatal ingestion error: " + ex.getMessage();
            job.setErrorDetails(d);
            job.setResultSummary(String.format("Processed=%d; Errors=%d; Fatal=true", processedCount, errorCount));
            logger.error("Job {} fatal error during ingestion: {}", job.getJobId(), ex.getMessage(), ex);
        }

        return job;
    }

    private String nullableText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String t = node.asText(null);
        if (t == null || t.isBlank()) return null;
        return t;
    }
}