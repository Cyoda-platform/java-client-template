package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.task.version_1.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TaskDependencyEvaluatorProcessorTest {

    @Mock
    private SerializerFactory serializerFactory;
    @Mock
    private ProcessorSerializer processorSerializer;
    @Mock
    private EntityService entityService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void dependencyEvaluator_restoresStatusWhenDependenciesSatisfied() throws Exception {
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(processorSerializer);
        TaskDependencyEvaluatorProcessor processor = new TaskDependencyEvaluatorProcessor(serializerFactory, entityService);

        Task task = new Task();
        task.setProjectId("PRJ-1");
        task.setTitle("T1");
        task.setStatus("blocked");
        task.setAssigneeId("user-1");
        task.setDependencies(Arrays.asList("dep-1"));

        ArrayNode arr = mapper.createArrayNode();
        ObjectNode dep = mapper.createObjectNode();
        dep.put("technicalId", "dep-1");
        dep.put("status", "completed");
        arr.add(dep);

        when(entityService.getItemsByCondition(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(CompletableFuture.completedFuture(arr));

        java.lang.reflect.Method method = TaskDependencyEvaluatorProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Task> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, task);

        Task result = (Task) method.invoke(processor, ctx);

        assertEquals("assigned", result.getStatus());
        assertNotNull(result.getUpdatedAt());
        if (result.getMetadata() != null) {
            assertFalse(result.getMetadata().containsKey("blockingDependencies"));
        }
    }
}
