package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class NotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public NotifySubscribersProcessor(SerializerFactory serializerFactory,
                                      EntityService entityService,
                                      ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .build();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        // We assume this processor is invoked when job is in SUCCEEDED or FAILED (per workflow)
        // Build notification payload containing job summary and relevant laureates
        try {
            ObjectNode basePayload = objectMapper.createObjectNode();
            basePayload.put("jobId", job.getJobId());
            basePayload.put("state", job.getState());
            if (job.getRecordsFetchedCount() != null) basePayload.put("recordsFetchedCount", job.getRecordsFetchedCount());
            basePayload.put("sourceEndpoint", job.getSourceEndpoint() == null ? "" : job.getSourceEndpoint());
            if (job.getScheduledAt() != null) basePayload.put("scheduledAt", job.getScheduledAt());
            if (job.getStartedAt() != null) basePayload.put("startedAt", job.getStartedAt());
            if (job.getFinishedAt() != null) basePayload.put("finishedAt", job.getFinishedAt());
            if (job.getErrorDetails() != null) basePayload.put("errorDetails", job.getErrorDetails());
            if (job.getMetadata() != null) basePayload.set("metadata", objectMapper.valueToTree(job.getMetadata()));

            // Fetch all laureates that reference this job via ingestJobId == job.jobId
            ArrayNode laureatesArray;
            try {
                SearchConditionRequest laureateCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.ingestJobId", "EQUALS", job.getJobId())
                );
                CompletableFuture<ArrayNode> laureatesFuture = entityService.getItemsByCondition(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        laureateCondition,
                        true
                );
                laureatesArray = laureatesFuture.join();
            } catch (Exception e) {
                logger.warn("Failed to fetch laureates for job {}: {}", job.getJobId(), e.getMessage());
                laureatesArray = objectMapper.createArrayNode();
            }
            basePayload.set("laureates", laureatesArray);

            // Fetch active subscribers
            ArrayNode subscribersArray;
            try {
                SearchConditionRequest subscriberCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.active", "EQUALS", "true")
                );
                CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition(
                        Subscriber.ENTITY_NAME,
                        String.valueOf(Subscriber.ENTITY_VERSION),
                        subscriberCondition,
                        true
                );
                subscribersArray = subscribersFuture.join();
            } catch (Exception e) {
                logger.warn("Failed to fetch subscribers: {}", e.getMessage());
                subscribersArray = objectMapper.createArrayNode();
            }

            // For each subscriber, prepare filtered payload and attempt delivery
            for (int i = 0; i < subscribersArray.size(); i++) {
                try {
                    ObjectNode subNode = (ObjectNode) subscribersArray.get(i);

                    // Extract technicalId if present to allow updating subscriber later
                    String technicalIdStr = null;
                    if (subNode.has("technicalId") && !subNode.get("technicalId").isNull()) {
                        technicalIdStr = subNode.get("technicalId").asText();
                    } else if (subNode.has("id") && !subNode.get("id").isNull()) {
                        technicalIdStr = subNode.get("id").asText();
                    }

                    // Build Subscriber object from node (use getters/setters only on resulting object)
                    Subscriber subscriber = new Subscriber();
                    if (subNode.has("subscriberId") && !subNode.get("subscriberId").isNull()) {
                        subscriber.setSubscriberId(subNode.get("subscriberId").asText());
                    }
                    if (subNode.has("active") && !subNode.get("active").isNull()) {
                        subscriber.setActive(subNode.get("active").asBoolean());
                    } else {
                        subscriber.setActive(Boolean.FALSE);
                    }
                    if (subNode.has("contactType") && !subNode.get("contactType").isNull()) {
                        subscriber.setContactType(subNode.get("contactType").asText());
                    }
                    // contactDetails
                    Subscriber.ContactDetails cd = new Subscriber.ContactDetails();
                    if (subNode.has("contactDetails") && subNode.get("contactDetails").has("url")
                            && !subNode.get("contactDetails").get("url").isNull()) {
                        cd.setUrl(subNode.get("contactDetails").get("url").asText());
                    }
                    subscriber.setContactDetails(cd);
                    // preferredPayload
                    if (subNode.has("preferredPayload") && !subNode.get("preferredPayload").isNull()) {
                        subscriber.setPreferredPayload(subNode.get("preferredPayload").asText());
                    } else {
                        subscriber.setPreferredPayload("full");
                    }
                    // filters
                    if (subNode.has("filters") && !subNode.get("filters").isNull()) {
                        Subscriber.Filters filters = new Subscriber.Filters();
                        ArrayNode cats = (ArrayNode) subNode.path("filters").path("categories");
                        ArrayNode years = (ArrayNode) subNode.path("filters").path("years");
                        List<String> categories = new ArrayList<>();
                        List<String> yearsList = new ArrayList<>();
                        if (cats != null) {
                            for (int j = 0; j < cats.size(); j++) {
                                if (!cats.get(j).isNull()) categories.add(cats.get(j).asText());
                            }
                        }
                        if (years != null) {
                            for (int j = 0; j < years.size(); j++) {
                                if (!years.get(j).isNull()) yearsList.add(years.get(j).asText());
                            }
                        }
                        filters.setCategories(categories);
                        filters.setYears(yearsList);
                        subscriber.setFilters(filters);
                    }

                    // Determine filtered laureates for this subscriber based on subscriber.filters
                    List<ObjectNode> laureatesForSubscriber = new ArrayList<>();
                    for (int li = 0; li < laureatesArray.size(); li++) {
                        ObjectNode laureateNode = (ObjectNode) laureatesArray.get(li);
                        boolean matches = true;
                        if (subscriber.getFilters() != null) {
                            // categories filter
                            List<String> cats = subscriber.getFilters().getCategories();
                            if (cats != null && !cats.isEmpty()) {
                                String laureateCategory = laureateNode.has("category") && !laureateNode.get("category").isNull()
                                        ? laureateNode.get("category").asText()
                                        : "";
                                boolean catMatch = cats.stream()
                                        .anyMatch(filterCat -> filterCat.equalsIgnoreCase(laureateCategory));
                                if (!catMatch) matches = false;
                            }
                            // years filter
                            List<String> yrs = subscriber.getFilters().getYears();
                            if (yrs != null && !yrs.isEmpty()) {
                                String laureateYear = laureateNode.has("year") && !laureateNode.get("year").isNull()
                                        ? laureateNode.get("year").asText()
                                        : "";
                                boolean yearMatch = yrs.stream()
                                        .anyMatch(filterYear -> filterYear.equalsIgnoreCase(laureateYear));
                                if (!yearMatch) matches = false;
                            }
                        }
                        if (matches) {
                            laureatesForSubscriber.add(laureateNode);
                        }
                    }

                    // If subscriber has filters and no laureates match, skip delivery.
                    if (subscriber.getFilters() != null) {
                        List<String> catFilt = subscriber.getFilters().getCategories();
                        List<String> yrsFilt = subscriber.getFilters().getYears();
                        boolean hasAnyFilter = (catFilt != null && !catFilt.isEmpty()) || (yrsFilt != null && !yrsFilt.isEmpty());
                        if (hasAnyFilter && laureatesForSubscriber.isEmpty()) {
                            logger.info("Skipping notification to subscriber {} because no laureates match filters", subscriber.getSubscriberId());
                            continue;
                        }
                    }

                    // Build subscriber-specific payload
                    ObjectNode subscriberPayload = basePayload.deepCopy();
                    ArrayNode payloadLaureatesNode = objectMapper.createArrayNode();
                    if ("summary".equalsIgnoreCase(subscriber.getPreferredPayload())) {
                        // include only summary fields
                        for (ObjectNode ln : laureatesForSubscriber) {
                            ObjectNode summary = objectMapper.createObjectNode();
                            if (ln.has("id") && !ln.get("id").isNull()) summary.put("id", ln.get("id").asInt());
                            if (ln.has("firstname") && !ln.get("firstname").isNull()) summary.put("firstname", ln.get("firstname").asText());
                            if (ln.has("surname") && !ln.get("surname").isNull()) summary.put("surname", ln.get("surname").asText());
                            if (ln.has("year") && !ln.get("year").isNull()) summary.put("year", ln.get("year").asText());
                            if (ln.has("category") && !ln.get("category").isNull()) summary.put("category", ln.get("category").asText());
                            payloadLaureatesNode.add(summary);
                        }
                    } else {
                        // full payload (use laureate nodes as-is)
                        for (ObjectNode ln : laureatesForSubscriber) {
                            payloadLaureatesNode.add(ln);
                        }
                    }
                    subscriberPayload.set("laureates", payloadLaureatesNode);

                    boolean delivered = false;
                    String deliveryError = null;

                    // Deliver according to contactType. Only webhook supported in this processor; log for others.
                    if (subscriber.getContactType() != null && subscriber.getContactType().equalsIgnoreCase("webhook")) {
                        String url = subscriber.getContactDetails() != null ? subscriber.getContactDetails().getUrl() : null;
                        if (url == null || url.isBlank()) {
                            deliveryError = "Missing webhook URL";
                            logger.warn("Subscriber {} has no webhook URL", subscriber.getSubscriberId());
                        } else {
                            try {
                                HttpRequest httpRequest = HttpRequest.newBuilder()
                                        .uri(new URI(url))
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(subscriberPayload)))
                                        .build();
                                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                                int status = response.statusCode();
                                delivered = status >= 200 && status < 300;
                                if (!delivered) {
                                    deliveryError = "Non-2xx response: " + status;
                                }
                                logger.info("Delivered to subscriber {} via webhook {}, status {}", subscriber.getSubscriberId(), url, status);
                            } catch (Exception e) {
                                deliveryError = e.getMessage();
                                logger.warn("Failed to deliver to subscriber {}: {}", subscriber.getSubscriberId(), e.getMessage());
                            }
                        }
                    } else if (subscriber.getContactType() != null && subscriber.getContactType().equalsIgnoreCase("email")) {
                        // Email delivery not implemented here — log and mark as not delivered
                        deliveryError = "Email delivery not implemented";
                        logger.info("Subscriber {} prefers email, but email delivery not implemented in processor", subscriber.getSubscriberId());
                    } else {
                        deliveryError = "Unsupported contactType: " + subscriber.getContactType();
                        logger.info("Unsupported contact type for subscriber {}: {}", subscriber.getSubscriberId(), subscriber.getContactType());
                    }

                    // If delivered, update subscriber.lastNotifiedAt
                    if (delivered && technicalIdStr != null) {
                        try {
                            Subscriber updatedSubscriber = new Subscriber();
                            // copy minimal fields necessary to pass validation in update (subscriberId, active, contactType, contactDetails, preferredPayload)
                            updatedSubscriber.setSubscriberId(subscriber.getSubscriberId());
                            updatedSubscriber.setActive(subscriber.getActive());
                            updatedSubscriber.setContactType(subscriber.getContactType());
                            updatedSubscriber.setContactDetails(subscriber.getContactDetails());
                            updatedSubscriber.setPreferredPayload(subscriber.getPreferredPayload());
                            updatedSubscriber.setFilters(subscriber.getFilters());
                            String nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
                            updatedSubscriber.setLastNotifiedAt(nowIso);

                            UUID techId = UUID.fromString(technicalIdStr);
                            CompletableFuture<UUID> updateFuture = entityService.updateItem(
                                    Subscriber.ENTITY_NAME,
                                    String.valueOf(Subscriber.ENTITY_VERSION),
                                    techId,
                                    updatedSubscriber
                            );
                            updateFuture.join();
                        } catch (Exception e) {
                            logger.warn("Failed to update subscriber lastNotifiedAt for {}: {}", subscriber.getSubscriberId(), e.getMessage());
                        }
                    }

                    // Optionally record per-subscriber delivery metadata in job.metadata
                    try {
                        Map<String, String> meta = job.getMetadata() == null ? new HashMap<>() : new HashMap<>(job.getMetadata());
                        String key = "notify:" + (subscriber.getSubscriberId() == null ? UUID.randomUUID().toString() : subscriber.getSubscriberId());
                        String value = delivered ? "DELIVERED" : ("FAILED:" + (deliveryError == null ? "unknown" : deliveryError));
                        meta.put(key, value);
                        job.setMetadata(meta);
                    } catch (Exception e) {
                        logger.debug("Unable to add delivery metadata for subscriber {}: {}", subscriber.getSubscriberId(), e.getMessage());
                    }
                } catch (Exception ex) {
                    logger.warn("Error processing subscriber index {}: {}", i, ex.getMessage());
                    // continue with next subscriber
                }
            }

        } catch (Exception ex) {
            logger.error("Unexpected error while notifying subscribers for job {}: {}", job.getJobId(), ex.getMessage(), ex);
            // capture error details on job
            String prev = job.getErrorDetails();
            String now = ex.getMessage();
            job.setErrorDetails((prev == null ? "" : prev + "\n") + now);
        }

        // Finalize job state: set to NOTIFIED_SUBSCRIBERS
        job.setState("NOTIFIED_SUBSCRIBERS");
        // Cyoda will persist this job entity automatically after processor returns
        return job;
    }
}