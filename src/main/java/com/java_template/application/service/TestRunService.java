package com.java_template.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.TestRunDTO;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Test Run operations
 */
@Service
public class TestRunService {

    private static final ModelSpec MODEL_SPEC =
            new ModelSpec().withName(TestRunDTO.ENTITY_NAME).withVersion(TestRunDTO.ENTITY_VERSION);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TestRunService(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    private TestRunDTO withId(EntityWithMetadata<TestRunDTO> result) {
        TestRunDTO entity = result.entity();
        entity.setId(result.getId());
        return entity;
    }

    private PageResult<TestRunDTO> toPage(PageResult<EntityWithMetadata<TestRunDTO>> result) {
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

    public TestRunDTO createTestRun(TestRunDTO testRun) {
        testRun.setStartedAt(LocalDateTime.now());
        TestRunDTO created = withId(entityService.create(testRun));
        // Trigger initialize_run workflow transition to activate SnapshotProcessor
        return withId(entityService.update(created.getId(), created, "initialize_run"));
    }

    public Optional<TestRunDTO> getTestRunById(UUID id) {
        try {
            return Optional.of(withId(entityService.getById(id, MODEL_SPEC, TestRunDTO.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public PageResult<TestRunDTO> getTestRunsByProjectId(UUID projectId, int page, int size) {
        SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                .pageNumber(page).pageSize(size).build();
        return toPage(entityService.search(MODEL_SPEC, conditionByField("projectId", projectId.toString()),
                TestRunDTO.class, params));
    }

    public List<TestRunDTO> getTestRunsByStatus(String status) {
        return entityService.search(MODEL_SPEC, conditionByField("status", status), TestRunDTO.class)
                .data().stream().map(this::withId).toList();
    }

    public List<TestRunDTO> getAllTestRuns() {
        return entityService.findAll(MODEL_SPEC, TestRunDTO.class).data()
                .stream().map(this::withId).toList();
    }

    public TestRunDTO updateTestRun(UUID id, TestRunDTO testRun) {
        return withId(entityService.update(id, testRun, null));
    }

    public Optional<TestRunDTO> completeTestRun(UUID id) {
        return getTestRunById(id).map(run -> {
            run.setCompletedAt(LocalDateTime.now());
            return withId(entityService.update(id, run, "complete_run"));
        });
    }

    public Optional<TestRunDTO> unlockTestRun(UUID id) {
        return getTestRunById(id).map(run ->
                withId(entityService.update(id, run, "unlock_run"))
        );
    }

    public boolean testRunExists(UUID id) {
        return getTestRunById(id).isPresent();
    }

    public boolean deleteTestRun(UUID id) {
        entityService.deleteById(id);
        return true;
    }
}

