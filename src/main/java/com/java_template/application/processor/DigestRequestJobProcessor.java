package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.ExternalApiData;
import com.java_template.application.entity.DigestEmail;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.java_template.common.service.EntityService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class DigestRequestJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DigestRequestJobProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("DigestRequestJobProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequestJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestRequestJob.class)
            .validate(DigestRequestJob::isValid, "Invalid DigestRequestJob entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestJobProcessor".equals(modelSpec.operationName()) &&
               "digestRequestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private DigestRequestJob processEntityLogic(DigestRequestJob job) {
        logger.info("Processing DigestRequestJob with email: {}", job.getEmail());

        try {
            // Validation
            if (!job.isValid()) {
                logger.error("DigestRequestJob validation failed for email: {}", job.getEmail());
                job.setStatus("FAILED");
                return job;
            }

            job.setStatus("PROCESSING");

            // Call external API
            String endpoint = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            if (job.getRequestMetadata() != null && !job.getRequestMetadata().isBlank()) {
                String metadata = job.getRequestMetadata().trim();
                if (metadata.startsWith("status=")) {
                    String statusValue = metadata.substring(7);
                    endpoint = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusValue;
                }
            }

            ExternalApiData apiData = new ExternalApiData();
            apiData.setJobTechnicalId(job.getModelKey().getName() + "-" + job.getEmail());
            apiData.setApiEndpoint(endpoint);
            apiData.setFetchedAt(Instant.now());

            // Simulate external API call (replace with actual HTTP call in real use)
            String response = "Sample API response data"; // Placeholder response
            apiData.setResponseData(response);

            if (!apiData.isValid()) {
                throw new RuntimeException("ExternalApiData validation failed");
            }

            CompletableFuture<UUID> apiDataIdFuture = entityService.addItem(
                    "ExternalApiData",
                    Config.ENTITY_VERSION,
                    apiData
            );
            UUID apiDataId = apiDataIdFuture.get();
            logger.info("ExternalApiData saved with ID: {}", apiDataId.toString());

            // Compile digest email content
            String emailContent = compileDigestContent(response);

            DigestEmail digestEmail = new DigestEmail();
            digestEmail.setJobTechnicalId(job.getModelKey().getName() + "-" + job.getEmail());
            digestEmail.setEmailContent(emailContent);
            digestEmail.setSentAt(null);
            digestEmail.setDeliveryStatus("PENDING");

            if (!digestEmail.isValid()) {
                throw new RuntimeException("DigestEmail validation failed");
            }

            CompletableFuture<UUID> digestEmailIdFuture = entityService.addItem(
                    "DigestEmail",
                    Config.ENTITY_VERSION,
                    digestEmail
            );
            UUID digestEmailId = digestEmailIdFuture.get();
            logger.info("DigestEmail saved with ID: {}", digestEmailId.toString());

            // Send email
            boolean emailSent = sendEmail(job.getEmail(), emailContent);
            if (emailSent) {
                digestEmail.setSentAt(Instant.now());
                digestEmail.setDeliveryStatus("SENT");
                logger.info("Email sent successfully to {}", job.getEmail());
            } else {
                digestEmail.setDeliveryStatus("FAILED");
                logger.error("Failed to send email to {}", job.getEmail());
            }

            // Update job status
            job.setStatus(emailSent ? "COMPLETED" : "FAILED");

        } catch (Exception e) {
            logger.error("Error processing DigestRequestJob: {}", e.getMessage());
            job.setStatus("FAILED");
        }

        return job;
    }

    private String compileDigestContent(String apiResponse) {
        if (apiResponse == null || apiResponse.isBlank()) {
            return "<html><body><p>No data available for digest.</p></body></html>";
        }
        return "<html><body><h3>Your Digest</h3><pre>" + apiResponse + "</pre></body></html>";
    }

    private boolean sendEmail(String recipientEmail, String content) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            logger.error("Recipient email is blank, cannot send email");
            return false;
        }
        logger.info("Simulating sending email to: {}", recipientEmail);
        return true;
    }
}
