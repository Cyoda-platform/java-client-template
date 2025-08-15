package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.HttpUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IngestProcessorTest {

    @Test
    public void testIngestWithSampleRecords() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer ps = Mockito.mock(ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService es = Mockito.mock(EntityService.class);
        ObjectMapper om = new ObjectMapper();
        HttpUtils hu = Mockito.mock(HttpUtils.class);

        // Mock addItem
        Mockito.when(es.addItem(Mockito.eq(Laureate.ENTITY_NAME), Mockito.eq(String.valueOf(Laureate.ENTITY_VERSION)), Mockito.any())).thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        IngestProcessor processor = new IngestProcessor(sf, es, om, hu);

        Job job = new Job();
        job.setId("job-1");
        job.setName("Job 1");
        job.setSchedule("now");
        job.setSourceEndpoint("http://example.com");
        job.setMaxAttempts(1);

        Map<String,Object> params = new HashMap<>();
        List<Map<String,Object>> samples = List.of(Map.of("id", 1, "firstname", "A", "surname", "B", "year", "2000", "category", "Chemistry"));
        params.put("sampleRecords", samples);
        job.setParameters(params);

        Method m = IngestProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Job> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, job);
        Job result = (Job) m.invoke(processor, ctx);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getProcessedRecordsCount());
        Assertions.assertEquals("INGESTING", result.getStatus());
    }
}
