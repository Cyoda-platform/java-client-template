package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequestJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DigestRequestJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final AtomicInteger digestDataRecordIdCounter = new AtomicInteger(1);
    private final AtomicInteger digestEmailRecordIdCounter = new AtomicInteger(1);

    public DigestRequestJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.restTemplate = new RestTemplate();
        logger.info("DigestRequestJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequestJob for request: {}", request.getId());
        return serializer.withRequest(request)
                .toEntity(DigestRequestJob.class)
                .validate(this::isValidEntity, "Invalid DigestRequestJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(DigestRequestJob entity) {
        return entity != null && entity.isValid();
    }

    private DigestRequestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DigestRequestJob> context) {
        DigestRequestJob job = context.entity();
        String technicalId = context.request().getEntityId();
        logger.info("Processing DigestRequestJob entity with technicalId: {}", technicalId);

        try {
            job.setStatus("PROCESSING");

            // Parse eventMetadata to determine petStatus
            String petStatus = "available"; // default
            String metadata = job.getEventMetadata();
            try {
                Map<String, Object> metadataMap = context.getObjectMapper().readValue(metadata, Map.class);
                if (metadataMap.containsKey("status")) {
                    petStatus = metadataMap.get("status").toString();
                }
            } catch (Exception e) {
                logger.error("Failed to parse eventMetadata for job {}: {}", technicalId, e.getMessage());
            }

            // Fetch data from external API
            String apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + petStatus;
            String apiResponse = "";
            try {
                apiResponse = restTemplate.getForObject(apiUrl, String.class);
            } catch (Exception e) {
                logger.error("Failed to fetch data from external API for job {}: {}", technicalId, e.getMessage());
                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now().toString());
                return job;
            }

            // Create DigestDataRecord entity
            com.java_template.application.entity.DigestDataRecord dataRecord = new com.java_template.application.entity.DigestDataRecord();
            dataRecord.setJobTechnicalId(technicalId);
            dataRecord.setApiEndpoint(apiUrl);
            dataRecord.setResponseData(apiResponse);
            dataRecord.setFetchedAt(Instant.now().toString());

            CompletableFuture<UUID> dataRecordIdFuture = entityService.addItem(
                    "DigestDataRecord",
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    dataRecord);
            UUID dataRecordUUID = dataRecordIdFuture.get();
            String dataRecordId = "data-" + digestDataRecordIdCounter.getAndIncrement();
            logger.info("Created DigestDataRecord {} for job {}", dataRecordId, technicalId);

            // Aggregate data into email content
            String emailContent = "<html><body><h3>Petstore Digest</h3><pre>" + apiResponse + "</pre></body></html>";

            // Create DigestEmailRecord entity
            com.java_template.application.entity.DigestEmailRecord emailRecord = new com.java_template.application.entity.DigestEmailRecord();
            emailRecord.setJobTechnicalId(technicalId);
            emailRecord.setEmailContent(emailContent);
            emailRecord.setEmailStatus("PENDING");

            CompletableFuture<UUID> emailRecordIdFuture = entityService.addItem(
                    "DigestEmailRecord",
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    emailRecord);
            UUID emailRecordUUID = emailRecordIdFuture.get();
            String emailRecordId = "email-" + digestEmailRecordIdCounter.getAndIncrement();
            logger.info("Created DigestEmailRecord {} for job {}", emailRecordId, technicalId);

            // Send email (simulate)
            boolean emailSent = sendEmail(job.getUserEmail(), "Your Petstore Digest", emailContent);

            emailRecord.setEmailStatus(emailSent ? "SENT" : "FAILED");
            emailRecord.setEmailSentAt(Instant.now().toString());

            job.setStatus(emailSent ? "COMPLETED" : "FAILED");
            job.setCompletedAt(Instant.now().toString());
            logger.info("Completed processing DigestRequestJob {} with status {}", technicalId, job.getStatus());

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Execution error in processDigestRequestJob {}: {}", technicalId, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in processDigestRequestJob {}: {}", technicalId, e.getMessage());
        }

        return job;
    }

    private boolean sendEmail(String to, String subject, String content) {
        logger.info("Sending email to {} with subject {}", to, subject);
        // Simulate email sending success
        return true;
    }

}
