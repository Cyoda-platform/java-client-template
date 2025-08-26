package com.java_template.application.processor;
import com.java_template.application.entity.batchjob.version_1.BatchJob;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ExecuteBatchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteBatchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ExecuteBatchProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BatchJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(BatchJob entity) {
        return entity != null && entity.isValid();
    }

    private BatchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<BatchJob> context) {
        BatchJob entity = context.entity();

        // 1) Start the job: mark startedAt and RUNNING
        String now = Instant.now().toString();
        entity.setStartedAt(now);
        entity.setStatus("RUNNING");

        // 2) Fetch users from Fakerest API
        List<User> createdUsers = new ArrayList<>();
        int fetchedCount = 0;
        int invalidCount = 0;

        try {
            String apiUrl = "https://fakerestapi.azurewebsites.net/api/v1/Users";
            logger.info("Fetching users from {}", apiUrl);
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String err = "Failed to fetch users, response code: " + responseCode;
                logger.error(err);
                entity.setStatus("FAILED");
                entity.setSummary(err);
                entity.setFinishedAt(Instant.now().toString());
                return entity;
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    sb.append(line);
                }
                JsonNode root = objectMapper.readTree(sb.toString());
                if (root != null && root.isArray()) {
                    for (JsonNode node : root) {
                        // Map fields conservatively using available JSON keys
                        User user = new User();
                        // id
                        if (node.has("id")) {
                            try { user.setId(node.get("id").asInt()); } catch (Exception ignored) {}
                        }
                        // username: try multiple possible keys
                        String username = node.has("userName") ? node.get("userName").asText("") :
                                          node.has("username") ? node.get("username").asText("") :
                                          (user.getId() != null ? "user" + user.getId() : "");
                        user.setUsername(username);

                        // fullName: try firstName + lastName or name
                        String full = "";
                        if (node.has("firstName") || node.has("lastName")) {
                            String f = node.has("firstName") ? node.get("firstName").asText("") : "";
                            String l = node.has("lastName") ? node.get("lastName").asText("") : "";
                            full = (f + " " + l).trim();
                        } else if (node.has("name")) {
                            full = node.get("name").asText("");
                        }
                        user.setFullName(full);

                        // email/phone/address best effort
                        if (node.has("email")) user.setEmail(node.get("email").asText(null));
                        if (node.has("phone")) user.setPhone(node.get("phone").asText(null));
                        if (node.has("address")) user.setAddress(node.get("address").asText(null));

                        // set initial validation status and timestamps
                        user.setValidationStatus("RAW");
                        user.setSourceFetchedAt(Instant.now());

                        // Basic user validity check before persisting: use User.isValid()
                        if (!user.isValid()) {
                            invalidCount++;
                            // still persist raw users to allow later processing, but mark invalid fields preserved
                        }

                        // Persist user via entityService
                        try {
                            CompletableFuture<UUID> idFuture = entityService.addItem(
                                User.ENTITY_NAME,
                                String.valueOf(User.ENTITY_VERSION),
                                user
                            );
                            // ensure creation completes
                            idFuture.join();
                            createdUsers.add(user);
                        } catch (Exception e) {
                            logger.error("Failed to add user entity: {}", e.getMessage(), e);
                        }
                    }
                    fetchedCount = root.size();
                } else {
                    logger.warn("No users array returned from Fakerest");
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception ex) {
            logger.error("Error during ingestion: {}", ex.getMessage(), ex);
            entity.setStatus("FAILED");
            entity.setSummary("Ingestion error: " + ex.getMessage());
            entity.setFinishedAt(Instant.now().toString());
            return entity;
        }

        // 3) Update BatchJob summary and move to GENERATING_REPORT
        String summary = String.format("ingested %d users, %d invalid", fetchedCount, invalidCount);
        entity.setSummary(summary);
        entity.setStatus("GENERATING_REPORT");

        // 4) Aggregate metrics (simple metrics based on this ingestion)
        int totalUsers = fetchedCount;
        int newUsers = fetchedCount - invalidCount;
        if (newUsers < 0) newUsers = 0;

        // 5) Create MonthlyReport entity
        try {
            MonthlyReport report = new MonthlyReport();
            report.setMonth(entity.getRunMonth());
            report.setGeneratedAt(Instant.now().toString());
            report.setTotalUsers(totalUsers);
            report.setNewUsers(newUsers);
            report.setInvalidUsers(invalidCount);
            report.setFileRef(String.format("reports/%s-user-report.pdf", entity.getRunMonth()));
            report.setStatus("CREATED");

            CompletableFuture<UUID> reportFuture = entityService.addItem(
                MonthlyReport.ENTITY_NAME,
                String.valueOf(MonthlyReport.ENTITY_VERSION),
                report
            );
            reportFuture.join();
        } catch (Exception e) {
            logger.error("Failed to create MonthlyReport: {}", e.getMessage(), e);
            entity.setStatus("FAILED");
            entity.setSummary("Report creation error: " + e.getMessage());
            entity.setFinishedAt(Instant.now().toString());
            return entity;
        }

        // 6) Leave job in GENERATING_REPORT state; downstream processors (e.g., RenderReportProcessor, NotifyAdminProcessor)
        // will continue the workflow. Do not set finishedAt here.
        logger.info("BatchJob {} ingestion completed: {}", entity.getJobName(), summary);

        return entity;
    }
}