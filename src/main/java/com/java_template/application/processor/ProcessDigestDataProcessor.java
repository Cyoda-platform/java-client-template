package com.java_template.application.processor;

import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ProcessDigestDataProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final com.java_template.common.service.EntityService entityService;

    public ProcessDigestDataProcessor(SerializerFactory serializerFactory, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        logger.info("ProcessDigestDataProcessor initialized with SerializerFactory and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestData for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(DigestData.class)
                .validate(this::isValidEntity, "Invalid DigestData entity")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ProcessDigestDataProcessor".equals(modelSpec.operationName()) &&
                "digestData".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(DigestData entity) {
        return entity.getDigestRequestId() != null && !entity.getDigestRequestId().isEmpty()
                && entity.getDataPayload() != null && !entity.getDataPayload().isEmpty();
    }

    private DigestData processEntityLogic(DigestData digestData) {
        logger.info("Processing DigestData event, id={}", digestData.getId());

        StringBuilder emailContent = new StringBuilder();
        emailContent.append("<html><body><h3>Your Digest Data</h3><pre>")
                .append(digestData.getDataPayload())
                .append("</pre></body></html>");

        DigestEmail digestEmail = new DigestEmail();
        digestEmail.setDigestRequestId(digestData.getDigestRequestId());
        digestEmail.setEmailContent(emailContent.toString());
        digestEmail.setSentStatus("PENDING");

        try {
            CompletableFuture<UUID> emailIdFuture = entityService.addItem(
                    "DigestEmail",
                    Config.ENTITY_VERSION,
                    digestEmail
            );
            UUID emailId = emailIdFuture.get();
            digestEmail.setId(emailId.toString());
            logger.info("Saved DigestEmail with id {}", emailId);

            // Trigger next processing step
            // In workflow this would be a transition to next state

        } catch (Exception e) {
            logger.error("Failed to save DigestEmail: {}", e.toString());
        }

        return digestData;
    }

}
