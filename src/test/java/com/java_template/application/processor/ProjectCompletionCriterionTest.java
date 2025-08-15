package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.project.version_1.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProjectCompletionCriterionTest {

    @Mock
    private SerializerFactory serializerFactory;
    @Mock
    private ProcessorSerializer processorSerializer;
    @Mock
    private EntityService entityService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void projectCompletion_whenAllTasksCompleted_setsProjectCompleted() throws Exception {
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(processorSerializer);
        ProjectCompletionCriterion processor = new ProjectCompletionCriterion(serializerFactory, entityService);

        Project project = new Project();
        project.setName("Proj");
        project.setId("PRJ-1");
        project.setStatus("in_progress");

        ArrayNode arr = mapper.createArrayNode();
        ObjectNode t1 = mapper.createObjectNode();
        t1.put("status", "completed");
        t1.put("technicalId", "t-1");
        arr.add(t1);

        when(entityService.getItemsByCondition(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(CompletableFuture.completedFuture(arr));

        java.lang.reflect.Method method = ProjectCompletionCriterion.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Project> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, project);

        Project result = (Project) method.invoke(processor, ctx);

        assertEquals("completed", result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertNotNull(result.getUpdatedAt());
    }
}
