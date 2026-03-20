package com.java_template.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.TestCaseDTO;
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
 * Service for Test Case operations
 */
@Service
public class TestCaseService {

    private static final ModelSpec MODEL_SPEC =
            new ModelSpec().withName(TestCaseDTO.ENTITY_NAME).withVersion(TestCaseDTO.ENTITY_VERSION);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TestCaseService(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    private TestCaseDTO withId(EntityWithMetadata<TestCaseDTO> result) {
        TestCaseDTO entity = result.entity();
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

    /**
     * Creates a new test case with ACTIVE status
     */
    public TestCaseDTO createTestCase(TestCaseDTO testCase) {
        testCase.setStatus("ACTIVE");
        testCase.setDeleted(false);
        return withId(entityService.create(testCase));
    }

    public Optional<TestCaseDTO> getTestCaseById(UUID id) {
        try {
            return Optional.of(withId(entityService.getById(id, MODEL_SPEC, TestCaseDTO.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<TestCaseDTO> getTestCasesBySuiteId(UUID suiteId) {
        return entityService.search(MODEL_SPEC, conditionByField("suiteId", suiteId.toString()), TestCaseDTO.class)
                .data().stream().map(this::withId).toList();
    }

    public List<TestCaseDTO> getAllTestCases() {
        return entityService.findAll(MODEL_SPEC, TestCaseDTO.class).data()
                .stream().map(this::withId).toList();
    }

    public TestCaseDTO updateTestCase(UUID id, TestCaseDTO testCase) {
        return withId(entityService.update(id, testCase, null));
    }

    public boolean deleteTestCase(UUID id) {
        return softDeleteTestCase(id);
    }

    public boolean testCaseExists(UUID id) {
        return getTestCaseById(id).isPresent();
    }

    public boolean softDeleteTestCase(UUID id) {
        return getTestCaseById(id).map(tc -> {
            tc.setDeleted(true);
            entityService.update(id, tc, null);
            return true;
        }).orElse(false);
    }
}

