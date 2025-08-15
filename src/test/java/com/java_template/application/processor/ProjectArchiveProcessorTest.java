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
public class ProjectArchiveProcessorTest {

    @Mock
    private SerializerFactory serializerFactory;
    @Mock
    private ProcessorSerializer processorSerializer;

    @Test
    public void archiveProject_setsArchivedStatusAndUpdatedAt() throws Exception {
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(processorSerializer);
        ProjectArchiveProcessor processor = new ProjectArchiveProcessor(serializerFactory);

        Project project = new Project();
        project.setName("P");
        project.setStatus("active");

        java.lang.reflect.Method method = ProjectArchiveProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Project> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, project);

        Project result = (Project) method.invoke(processor, ctx);

        assertEquals("archived", result.getStatus());
        assertNotNull(result.getUpdatedAt());
    }
}
