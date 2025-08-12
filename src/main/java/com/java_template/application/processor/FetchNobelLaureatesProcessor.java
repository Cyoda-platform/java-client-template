package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.service.search.Condition;
import com.java_template.common.service.search.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class FetchNobelLaureatesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchNobelLaureatesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public FetchNobelLaureatesProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid Job entity state")
            .map(contextEntity -> {
                Job job = contextEntity.entity();

                // Transition the job status to INGESTING
                job.setStatus("INGESTING");
                logger.info("Job {} status updated to INGESTING", job.getJobId());

                // Extract parameters JSON string from job and parse it
                String parametersJson = job.getParameters();
                Map<String, Object> parametersMap;
                try {
                    parametersMap = objectMapper.readValue(parametersJson, Map.class);
                } catch (Exception e) {
                    logger.error("Failed to parse job parameters JSON", e);
                    job.setStatus("FAILED");
                    job.setResultSummary("Failed to parse job parameters JSON");
                    return job;
                }

                // Fetch Nobel laureates data from OpenDataSoft API
                List<Map<String, Object>> laureatesData;
                try {
                    laureatesData = fetchLaureatesData(parametersMap);
                } catch (Exception e) {
                    logger.error("Failed to fetch laureates data from API", e);
                    job.setStatus("FAILED");
                    job.setResultSummary("Failed to fetch laureates data from API");
                    return job;
                }

                int successCount = 0;
                int failureCount = 0;

                // For each laureate data, create a new immutable Laureate entity and process it
                for (Map<String, Object> laureateData : laureatesData) {
                    try {
                        Laureate laureate = createLaureateEntity(laureateData);

                        // Persist laureate entity asynchronously
                        CompletableFuture<UUID> addFuture = entityService.addItem(
                            Laureate.ENTITY_NAME,
                            String.valueOf(Laureate.ENTITY_VERSION),
                            laureate
                        );

                        // Trigger Laureate processing (validation, enrichment)
                        addFuture.thenAccept(id -> {
                            try {
                                entityService.processEntity(
                                    Laureate.ENTITY_NAME,
                                    String.valueOf(Laureate.ENTITY_VERSION),
                                    id
                                );
                                logger.info("Triggered processing for Laureate id {}", laureate.getLaureateId());
                            } catch (Exception ex) {
                                logger.error("Failed to trigger processing for Laureate id {}", laureate.getLaureateId(), ex);
                            }
                        }).join();

                        successCount++;
                    } catch (Exception e) {
                        logger.error("Failed to create or process Laureate entity", e);
                        failureCount++;
                    }
                }

                // Update job status based on ingestion results
                if (failureCount == 0) {
                    job.setStatus("SUCCEEDED");
                    job.setResultSummary("Successfully ingested " + successCount + " laureates.");
                    logger.info("Job {} ingestion succeeded with {} laureates ingested", job.getJobId(), successCount);
                } else {
                    job.setStatus("FAILED");
                    job.setResultSummary("Ingestion completed with " + failureCount + " failures.");
                    logger.warn("Job {} ingestion failed with {} failed laureate creations", job.getJobId(), failureCount);
                }

                // Set completedAt timestamp
                job.setCompletedAt(OffsetDateTime.now());

                // Notify active subscribers
                try {
                    notifyActiveSubscribers();
                    job.setStatus("NOTIFIED_SUBSCRIBERS");
                    logger.info("Job {} status updated to NOTIFIED_SUBSCRIBERS", job.getJobId());
                } catch (Exception e) {
                    logger.error("Failed to notify subscribers", e);
                }

                return job;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private List<Map<String, Object>> fetchLaureatesData(Map<String, Object> parameters) throws Exception {
        String baseUrl = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/nobel-prize-laureates/records";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch data, status code: " + response.statusCode());
        }

        Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
        if (!responseMap.containsKey("records")) {
            throw new RuntimeException("Response does not contain records");
        }

        List<Map<String, Object>> records = (List<Map<String, Object>>) responseMap.get("records");

        return records.stream()
            .map(record -> (Map<String, Object>) record.get("fields"))
            .collect(Collectors.toList());
    }

    private Laureate createLaureateEntity(Map<String, Object> laureateData) {
        Laureate laureate = new Laureate();

        Object idObj = laureateData.get("id");
        if (idObj != null) {
            laureate.setLaureateId(String.valueOf(idObj));
        }

        laureate.setFirstname((String) laureateData.get("firstname"));
        laureate.setSurname((String) laureateData.get("surname"));
        laureate.setBorn((String) laureateData.get("born"));
        laureate.setDied((String) laureateData.get("died"));
        laureate.setBorncountry((String) laureateData.get("borncountry"));
        laureate.setBorncountrycode((String) laureateData.get("borncountrycode"));
        laureate.setBorncity((String) laureateData.get("borncity"));
        laureate.setGender((String) laureateData.get("gender"));
        laureate.setYear((String) laureateData.get("year"));
        laureate.setCategory((String) laureateData.get("category"));
        laureate.setMotivation((String) laureateData.get("motivation"));
        laureate.setName((String) laureateData.get("name"));
        laureate.setCity((String) laureateData.get("city"));
        laureate.setCountry((String) laureateData.get("country"));

        logger.info("Created Laureate entity with id {}", laureate.getLaureateId());
        return laureate;
    }

    private void notifyActiveSubscribers() throws Exception {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$.active", "EQUALS", true)
        );

        CompletableFuture<List<Object>> futureSubscribers = entityService.getItemsByCondition(
            Subscriber.ENTITY_NAME,
            String.valueOf(Subscriber.ENTITY_VERSION),
            condition,
            true
        ).thenApply(arrayNode -> arrayNode.findValuesAsText("subscriberId"));

        List<String> subscriberIds = futureSubscribers.get();

        for (String subscriberId : subscriberIds) {
            // Fetch subscriber entity by subscriberId
            CompletableFuture<Object> subscriberFuture = entityService.getItem(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                UUID.fromString(subscriberId)
            );

            Subscriber subscriber = (Subscriber) subscriberFuture.get();

            if (subscriber != null) {
                // Trigger subscriber processing (e.g., send notification)
                entityService.processEntity(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    UUID.fromString(subscriber.getSubscriberId())
                );
                logger.info("Notified subscriber id {}", subscriber.getSubscriberId());
            }
        }
    }
}
