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
public class ProjectEnterInProgressProcessorTest {

    @Mock
    private SerializerFactory serializerFactory;
    @Mock
    private ProcessorSerializer processorSerializer;
    @Mock
    private EntityService entityService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void enterInProgress_setsStatusWhenTaskInProgressExists() throws Exception {
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(processorSerializer);
        ProjectEnterInProgressProcessor processor = new ProjectEnterInProgressProcessor(serializerFactory, entityService);

        Project project = new Project();
        project.setName("P");
        project.setId("PRJ-1");
        project.setStatus("active");

        ArrayNode arr = mapper.createArrayNode();
        ObjectNode t1 = mapper.createObjectNode();
        t1.put("status", "in_progress");
        t1.put("technicalId", "t-1");
        arr.add(t1);

        when(entityService.getItemsByCondition(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(CompletableFuture.completedFuture(arr));

        java.lang.reflect.Method method = ProjectEnterInProgressProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Project> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, project);

        Project result = (Project) method.invoke(processor, ctx);

        assertEquals("in_progress", result.getStatus());
        assertNotNull(result.getUpdatedAt());
    }
}
