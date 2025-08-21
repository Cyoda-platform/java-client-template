package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.searchrequest.version_1.SearchRequest;
import com.java_template.application.entity.rawpet.version_1.RawPet;
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

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class IngestPetsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestPetsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public IngestPetsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestPets for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(SearchRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SearchRequest entity) {
        return entity != null && "INGESTING".equals(entity.getState());
    }

    private SearchRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SearchRequest> context) {
        SearchRequest entity = context.entity();
        // mark ingestion started
        try {
            entity.setIngestionStarted(true);
        } catch (Exception e) {
            logger.debug("SearchRequest does not expose setIngestionStarted - continuing", e);
        }

        // Build external API URL based on filters
        try {
            String baseUrl = "https://petstore.swagger.io/v2/pet/findByStatus"; // from user requirements
            String status = entity.getStatus();
            String species = entity.getSpecies();
            int page = entity.getPage() == null ? 1 : entity.getPage();
            int pageSize = entity.getPageSize() == null ? 20 : entity.getPageSize();

            // For this example, we'll call the public API by status only because upstream API supports status filter
            String apiUrl = baseUrl + "?status=" + (status == null ? "available" : status);
            logger.info("Calling external API: {}", apiUrl);

            // Simple HTTP call using ObjectMapper to simulate; in real code use WebClient/RestTemplate with retries
            java.net.URL url = new java.net.URL(apiUrl);
            try (java.io.InputStream is = url.openStream()) {
                JsonNode root = objectMapper.readTree(is);
                if (root != null && root.isArray()) {
                    Iterator<JsonNode> it = root.elements();
                    while (it.hasNext()) {
                        JsonNode pet = it.next();
                        // extract rawId - use id if present
                        String rawId = pet.has("id") ? pet.get("id").asText() : UUID.randomUUID().toString();
                        // deduplicate by rawId + searchRequestId
                        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.rawId", "EQUALS", rawId),
                            Condition.of("$.searchRequestId", "EQUALS", entity.getTechnicalId())
                        );
                        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> existingFuture = entityService.getItemsByCondition(
                            RawPet.ENTITY_NAME, String.valueOf(RawPet.ENTITY_VERSION), condition, true
                        );
                        com.fasterxml.jackson.databind.node.ArrayNode existing = existingFuture.get();
                        if (existing != null && existing.size() > 0) {
                            logger.debug("RawPet {} already exists for searchRequest {} - skipping", rawId, entity.getTechnicalId());
                            continue;
                        }

                        RawPet raw = new RawPet();
                        raw.setRawId(rawId);
                        raw.setPayload(pet.toString());
                        raw.setSpecies(pet.has("species") ? pet.get("species").asText(null) : null);
                        raw.setStatus(pet.has("status") ? pet.get("status").asText(null) : null);
                        if (pet.has("category") && pet.get("category").has("id")) {
                            raw.setCategoryId(pet.get("category").get("id").asInt());
                        }
                        raw.setIngestedAt(Instant.now().toString());
                        raw.setSearchRequestId(entity.getTechnicalId());
                        raw.setState("STORED");

                        // persist raw pet
                        try {
                            entityService.addItem(RawPet.ENTITY_NAME, String.valueOf(RawPet.ENTITY_VERSION), raw);
                            logger.info("Stored RawPet {} for SearchRequest {}", rawId, entity.getTechnicalId());
                        } catch (Exception e) {
                            logger.error("Failed to store RawPet {}", rawId, e);
                            // mark ingestion failed and create notification? For now set state
                            entity.setState("INGESTION_FAILED");
                            return entity;
                        }
                    }
                }
            }

            // After ingestion attempt, move to TRANSFORMING (transformation processors will evaluate counts)
            entity.setState("TRANSFORMING");
            logger.info("SearchRequest {} ingestion completed -> TRANSFORMING", entity.getTechnicalId());
        } catch (Exception e) {
            logger.error("Error during ingestion for SearchRequest {}", entity.getTechnicalId(), e);
            entity.setState("INGESTION_FAILED");
        }
        return entity;
    }
}
