package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.HttpUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NotifySubscribersProcessorTest {

    @Test
    public void testNotifySuccess() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        ProcessorSerializer ps = Mockito.mock(ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        EntityService es = Mockito.mock(EntityService.class);
        ObjectMapper om = new ObjectMapper();
        HttpUtils hu = Mockito.mock(HttpUtils.class);

        // Prepare subscribers array
        ArrayNode arr = om.createArrayNode();
        ObjectNode subNode = om.createObjectNode();
        subNode.put("technicalId", UUID.randomUUID().toString());
        subNode.put("name", "Sub1");
        subNode.put("email", "sub1@example.com");
        subNode.putArray("channels").add("EMAIL");
        subNode.put("active", true);
        arr.add(subNode);

        Mockito.when(es.getItems(Mockito.eq(Subscriber.ENTITY_NAME), Mockito.eq(String.valueOf(Subscriber.ENTITY_VERSION)))).thenReturn(CompletableFuture.completedFuture(arr));

        NotifySubscribersProcessor processor = new NotifySubscribersProcessor(sf, es, om, hu);

        Job job = new Job();
        job.setId("job-1");
        job.setName("Job 1");
        job.setSchedule("now");
        job.setSourceEndpoint("http://example.com");
        job.setProcessedRecordsCount(5);
        job.setStatus("SUCCEEDED");

        Method m = NotifySubscribersProcessor.class.getDeclaredMethod("processEntityLogic", ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        ProcessorSerializer.ProcessorEntityExecutionContext<Job> ctx = new ProcessorSerializer.ProcessorEntityExecutionContext<>(null, job);
        Job result = (Job) m.invoke(processor, ctx);

        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getLastNotifiedAt());
    }
}
