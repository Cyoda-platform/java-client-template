package com.java_template.application.processor;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.application.entity.project.version_1.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProjectCompleteProcessorTest {

    @Mock
    private SerializerFactory serializerFactory;
    @Mock
    private ProcessorSerializer processorSerializer;

    @Test
    public void completeProject_setsCompletedAndTimestamps() throws Exception {
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(processorSerializer);
        ProjectCompleteProcessor processor = new ProjectCompleteProcessor(serializerFactory);

        Project project = new Project();
        project.setName("P");
        project.setStatus("in_progress");

        java.lang.reflect.Method method = ProjectCompleteProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Project> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, project);

        Project result = (Project) method.invoke(processor, ctx);

        assertEquals("completed", result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertNotNull(result.getUpdatedAt());
    }
}
