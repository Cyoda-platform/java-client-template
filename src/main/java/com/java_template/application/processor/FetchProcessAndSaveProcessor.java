package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.getuserjob.version_1.GetUserJob;
import com.java_template.application.entity.getuserresult.version_1.GetUserResult;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class FetchProcessAndSaveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchProcessAndSaveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public FetchProcessAndSaveProcessor(SerializerFactory serializerFactory,
                                       EntityService entityService,
                                       ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing GetUserJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(GetUserJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(GetUserJob entity) {
        return entity != null && entity.isValid();
    }

    private GetUserJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<GetUserJob> context) {
        GetUserJob job = context.entity();
        EntityProcessorCalculationRequest request = context.request();
        String technicalId = request.getId();

        // Validation of request_user_id
        String reqUserId = job.getRequestUserId();
        if (reqUserId == null || reqUserId.isBlank()) {
            job.setStatus("FAILED");
            job.setErrorMessage("User ID is required");
            job.setCompletedAt(Instant.now().toString());
            logger.warn("Validation failed: missing user id for job {}", technicalId);
            return job;
        }

        int userId;
        try {
            userId = Integer.parseInt(reqUserId.trim());
            if (userId <= 0) throw new NumberFormatException("non-positive");
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage("User ID must be a positive integer");
            job.setCompletedAt(Instant.now().toString());
            logger.warn("Validation failed: invalid user id '{}' for job {}", reqUserId, technicalId);
            return job;
        }

        // Start processing / fetching
        job.setStartedAt(Instant.now().toString());
        job.setStatus("STARTED");

        String url = "https://reqres.in/api/users/" + userId;
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(url))
            .build();

        try {
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = httpResponse.statusCode();
            job.setResponseCode(statusCode);

            if (statusCode == 200) {
                // Parse user payload
                String body = httpResponse.body();
                JsonNode root = objectMapper.readTree(body);
                JsonNode data = root.path("data");

                User user = new User();
                if (data.hasNonNull("id")) user.setId(data.get("id").asInt());
                if (data.hasNonNull("email")) user.setEmail(data.get("email").asText());
                if (data.hasNonNull("first_name")) user.setFirstName(data.get("first_name").asText());
                if (data.hasNonNull("last_name")) user.setLastName(data.get("last_name").asText());
                if (data.hasNonNull("avatar")) user.setAvatar(data.get("avatar").asText());
                user.setRetrievedAt(Instant.now().toString());
                user.setSource("ReqRes");

                // Persist User (other entity)
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    user
                );
                // fire-and-forget persist; do not block processing thread longer than necessary
                idFuture.whenComplete((uuid, ex) -> {
                    if (ex != null) {
                        logger.warn("Failed to persist User for job {}: {}", technicalId, ex.getMessage());
                    } else {
                        logger.info("Persisted User {} for job {}", uuid, technicalId);
                    }
                });

                // Build result
                GetUserResult result = new GetUserResult();
                result.setJobReference(technicalId);
                result.setStatus("SUCCESS");
                result.setRetrievedAt(Instant.now().toString());
                result.setErrorMessage(null);
                result.setUser(user);

                CompletableFuture<UUID> resultFuture = entityService.addItem(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION),
                    result
                );
                resultFuture.whenComplete((uuid, ex) -> {
                    if (ex != null) {
                        logger.warn("Failed to persist GetUserResult for job {}: {}", technicalId, ex.getMessage());
                    } else {
                        logger.info("Persisted GetUserResult {} for job {}", uuid, technicalId);
                    }
                });

                job.setStatus("COMPLETED");
                job.setCompletedAt(Instant.now().toString());
                job.setErrorMessage(null);
                return job;

            } else if (statusCode == 404) {
                GetUserResult result = new GetUserResult();
                result.setJobReference(technicalId);
                result.setStatus("NOT_FOUND");
                result.setRetrievedAt(Instant.now().toString());
                result.setErrorMessage("User not found");
                result.setUser(null);

                entityService.addItem(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION),
                    result
                ).whenComplete((uuid, ex) -> {
                    if (ex != null) {
                        logger.warn("Failed to persist NOT_FOUND GetUserResult for job {}: {}", technicalId, ex.getMessage());
                    } else {
                        logger.info("Persisted NOT_FOUND GetUserResult {} for job {}", uuid, technicalId);
                    }
                });

                job.setStatus("COMPLETED");
                job.setCompletedAt(Instant.now().toString());
                job.setErrorMessage(null);
                return job;
            } else {
                GetUserResult result = new GetUserResult();
                result.setJobReference(technicalId);
                result.setStatus("ERROR");
                result.setRetrievedAt(Instant.now().toString());
                result.setErrorMessage("Upstream returned code " + statusCode);
                result.setUser(null);

                entityService.addItem(
                    GetUserResult.ENTITY_NAME,
                    String.valueOf(GetUserResult.ENTITY_VERSION),
                    result
                ).whenComplete((uuid, ex) -> {
                    if (ex != null) {
                        logger.warn("Failed to persist ERROR GetUserResult for job {}: {}", technicalId, ex.getMessage());
                    } else {
                        logger.info("Persisted ERROR GetUserResult {} for job {}", uuid, technicalId);
                    }
                });

                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now().toString());
                job.setErrorMessage("Upstream returned code " + statusCode);
                return job;
            }

        } catch (Exception e) {
            logger.error("Exception while fetching user {} for job {}: {}", userId, technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setErrorMessage("Exception during fetch: " + e.getMessage());
            job.setCompletedAt(Instant.now().toString());
            return job;
        }
    }
}