package com.java_template.application.processor;

import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.application.entity.task.version_1.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TaskCompleteProcessorTest {

    @Mock
    private SerializerFactory serializerFactory;
    @Mock
    private ProcessorSerializer processorSerializer;

    @Test
    public void completeTask_setsCompletedAndTimestamps() throws Exception {
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(processorSerializer);
        TaskCompleteProcessor processor = new TaskCompleteProcessor(serializerFactory);

        Task task = new Task();
        task.setProjectId("PRJ-1");
        task.setTitle("T1");
        task.setStatus("in_progress");

        java.lang.reflect.Method method = TaskCompleteProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Task> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, task);

        Task result = (Task) method.invoke(processor, ctx);

        assertEquals("completed", result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertNotNull(result.getUpdatedAt());
    }
}
