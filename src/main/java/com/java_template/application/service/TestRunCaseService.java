package com.java_template.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.TestRunCaseDTO;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
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
 * Service for Test Run Case operations
 */
@Service
public class TestRunCaseService {

    private static final ModelSpec MODEL_SPEC =
            new ModelSpec().withName(TestRunCaseDTO.ENTITY_NAME).withVersion(TestRunCaseDTO.ENTITY_VERSION);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TestRunCaseService(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    private TestRunCaseDTO withId(EntityWithMetadata<TestRunCaseDTO> result) {
        TestRunCaseDTO entity = result.entity();
        entity.setId(result.getId());
        return entity;
    }

    private PageResult<TestRunCaseDTO> toPage(PageResult<EntityWithMetadata<TestRunCaseDTO>> result) {
        return PageResult.of(result.searchId(),
                result.data().stream().map(this::withId).toList(),
                result.pageNumber(), result.pageSize(), result.totalElements());
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

    public TestRunCaseDTO createTestRunCase(TestRunCaseDTO testRunCase) {
        testRunCase.setStatus("UNTESTED");
        return withId(entityService.create(testRunCase));
    }

    public Optional<TestRunCaseDTO> getTestRunCaseById(UUID id) {
        try {
            return Optional.of(withId(entityService.getById(id, MODEL_SPEC, TestRunCaseDTO.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public PageResult<TestRunCaseDTO> getTestRunCasesByTestRunId(UUID testRunId, int page, int size) {
        SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                .pageNumber(page).pageSize(size).build();
        return toPage(entityService.search(MODEL_SPEC, conditionByField("testRunId", testRunId.toString()),
                TestRunCaseDTO.class, params));
    }

    public Optional<TestRunCaseDTO> updateTestRunCaseStatus(UUID id, String status) {
        return getTestRunCaseById(id).map(trc -> {
            trc.setStatus(status);
            return withId(entityService.update(id, trc, null));
        });
    }

    public Optional<TestRunCaseDTO> linkBug(UUID id, String bugUrl) {
        return getTestRunCaseById(id).map(trc -> {
            trc.setBugUrl(bugUrl);
            return withId(entityService.update(id, trc, null));
        });
    }
}

