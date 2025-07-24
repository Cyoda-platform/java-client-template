package com.java_template.application.processor;

import com.java_template.application.entity.MailStatusEnum;
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

import java.util.Map;

@Component
public class MailStatusEnumProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public MailStatusEnumProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("MailStatusEnumProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail entity for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(com.java_template.application.entity.Mail.class)
                .validate(this::isValidEntity, "Invalid Mail entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MailStatusEnumProcessor".equals(modelSpec.operationName()) &&
                "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(com.java_template.application.entity.Mail mail) {
        // Basic validation example: must have non-null mailList and status
        return mail.getMailList() != null && mail.getStatus() != null;
    }

    private com.java_template.application.entity.Mail processEntityLogic(com.java_template.application.entity.Mail mail) {
        // Implementing the business logic from the functional requirement 2 (processMail Flow)

        // 2. Criteria Evaluation: Simulate criteria evaluation by counting "isHappy" values in criteriaResults
        Map<String, String> criteriaResults = mail.getCriteriaResults();

        if(criteriaResults == null || criteriaResults.isEmpty()) {
            logger.warn("No criteria results found for Mail id: {}", mail.getId());
            return mail; // no processing if no criteria results
        }

        long happyCount = criteriaResults.values().stream().filter("isHappy"::equals).count();
        long gloomyCount = criteriaResults.size() - happyCount;

        // 3. Determine Overall Mood
        if(happyCount > gloomyCount) {
            mail.setIsHappy(true);
            mail.setIsGloomy(false);
            mail.setStatus(MailStatusEnum.PROCESSING);
        } else {
            mail.setIsHappy(false);
            mail.setIsGloomy(true);
            mail.setStatus(MailStatusEnum.PROCESSING);
        }

        // Note: Sending mail logic and status update to SENT_HAPPY or SENT_GLOOMY
        // is handled by other processors or workflow transitions, not here.

        return mail;
    }
}
