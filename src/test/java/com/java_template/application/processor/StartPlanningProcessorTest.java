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
public class StartPlanningProcessorTest {

    @Mock
    private SerializerFactory serializerFactory;
    @Mock
    private ProcessorSerializer processorSerializer;

    @Test
    public void startPlanning_setsPlanningAndInitializesMetadata() throws Exception {
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(processorSerializer);
        StartPlanningProcessor processor = new StartPlanningProcessor(serializerFactory);

        Project project = new Project();
        project.setName("Test Project");
        project.setStatus("created");

        java.lang.reflect.Method method = StartPlanningProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Project> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, project);

        Project result = (Project) method.invoke(processor, ctx);

        assertEquals("planning", result.getStatus());
        assertNotNull(result.getUpdatedAt());
        assertNotNull(result.getMetadata());
        assertTrue(Boolean.TRUE.equals(result.getMetadata().get("milestonesInitialized")));
    }
}
