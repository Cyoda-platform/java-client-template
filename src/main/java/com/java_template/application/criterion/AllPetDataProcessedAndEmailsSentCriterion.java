package com.java_template.application.criterion;

import com.java_template.application.entity.DigestJob;
import com.java_template.application.entity.PetDataRecord;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AllPetDataProcessedAndEmailsSentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public AllPetDataProcessedAndEmailsSentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("AllPetDataProcessedAndEmailsSentCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(DigestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AllPetDataProcessedAndEmailsSentCriterion".equals(modelSpec.operationName()) &&
               "digestJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(DigestJob digestJob) {
        // Assume context or repository access to pet data records and email dispatches
        // Here we simulate validation logic
        List<PetDataRecord> petDataRecords = fetchPetDataRecordsForJob(digestJob.getId());
        if (petDataRecords == null || petDataRecords.isEmpty()) {
            return EvaluationOutcome.fail("No pet data records found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        boolean allProcessed = petDataRecords.stream()
            .allMatch(record -> "PROCESSED".equalsIgnoreCase(record.getStatus()));
        if (!allProcessed) {
            return EvaluationOutcome.fail("Not all pet data records are processed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        List<EmailDispatch> emailDispatches = fetchEmailDispatchesForJob(digestJob.getId());
        if (emailDispatches == null || emailDispatches.isEmpty()) {
            return EvaluationOutcome.fail("No email dispatches found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        boolean allSent = emailDispatches.stream()
            .allMatch(dispatch -> "SENT".equalsIgnoreCase(dispatch.getStatus()));
        if (!allSent) {
            return EvaluationOutcome.fail("Not all emails have been sent successfully", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    // Dummy methods to simulate data retrieval
    private List<PetDataRecord> fetchPetDataRecordsForJob(String jobId) {
        // Implementation should query the data source to get pet data records for the job
        return List.of(); // Return empty for placeholder
    }

    private List<EmailDispatch> fetchEmailDispatchesForJob(String jobId) {
        // Implementation should query the data source to get email dispatches for the job
        return List.of(); // Return empty for placeholder
    }
}
