package com.java_template.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.TestRunStepDTO;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Test Run Step operations
 */
@Service
public class TestRunStepService {

    private static final ModelSpec MODEL_SPEC =
            new ModelSpec().withName(TestRunStepDTO.ENTITY_NAME).withVersion(TestRunStepDTO.ENTITY_VERSION);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TestRunStepService(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    private TestRunStepDTO withId(EntityWithMetadata<TestRunStepDTO> result) {
        TestRunStepDTO entity = result.entity();
        entity.setId(result.getId());
        return entity;
    }

    private GroupCondition conditionByField(String fieldName, Object value) {
        SimpleCondition condition = new SimpleCondition()
                .withJsonPath("$." + fieldName)
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree(value));
        return new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(condition));
    }

    public TestRunStepDTO createTestRunStep(TestRunStepDTO testRunStep) {
        testRunStep.setStatus("UNTESTED");
        return withId(entityService.create(testRunStep));
    }

    public Optional<TestRunStepDTO> getTestRunStepById(UUID id) {
        try {
            return Optional.of(withId(entityService.getById(id, MODEL_SPEC, TestRunStepDTO.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<TestRunStepDTO> getTestRunStepsByTestRunCaseId(UUID testRunCaseId) {
        return entityService.search(MODEL_SPEC, conditionByField("testRunCaseId", testRunCaseId.toString()), TestRunStepDTO.class)
                .data().stream().map(this::withId).toList();
    }

    public Optional<TestRunStepDTO> updateTestRunStepStatus(UUID id, String status) {
        return getTestRunStepById(id).map(trs -> {
            trs.setStatus(status);
            return withId(entityService.update(id, trs, null));
        });
    }

    public Optional<TestRunStepDTO> linkBug(UUID id, String bugUrl) {
        return getTestRunStepById(id).map(trs -> {
            trs.setActualResult(bugUrl);
            return withId(entityService.update(id, trs, null));
        });
    }
}

