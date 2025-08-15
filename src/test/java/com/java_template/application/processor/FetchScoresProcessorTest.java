package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.common.util.HttpUtils;
import com.java_template.common.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class FetchScoresProcessorTest {

    @Test
    public void testFetchSuccess() throws Exception {
        SerializerFactory sf = Mockito.mock(SerializerFactory.class);
        com.java_template.common.serializer.ProcessorSerializer ps = Mockito.mock(com.java_template.common.serializer.ProcessorSerializer.class);
        Mockito.when(sf.getDefaultProcessorSerializer()).thenReturn(ps);

        HttpUtils httpUtils = Mockito.mock(HttpUtils.class);
        JsonUtils jsonUtils = Mockito.mock(JsonUtils.class);

        FetchScoresProcessor processor = new FetchScoresProcessor(sf, httpUtils, jsonUtils);

        FetchJob job = new FetchJob();
        job.setRequestDate("2025-03-25");
        job.setScheduledTime("18:00Z");

        ObjectNode resp = Mockito.mock(ObjectNode.class);
        Mockito.when(resp.has("status")).thenReturn(true);
        Mockito.when(resp.get("status")).thenReturn(com.fasterxml.jackson.databind.node.IntNode.valueOf(200));
        Mockito.when(resp.get("json")).thenReturn(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());

        CompletableFuture<ObjectNode> future = CompletableFuture.completedFuture(resp);
        Mockito.when(httpUtils.sendGetRequest(Mockito.isNull(), Mockito.anyString(), Mockito.anyString())).thenReturn(future);

        java.lang.reflect.Method m = FetchScoresProcessor.class.getDeclaredMethod("processEntityLogic", com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext.class);
        m.setAccessible(true);
        FetchJob out = (FetchJob) m.invoke(processor, new com.java_template.common.serializer.ProcessorSerializer.ProcessorEntityExecutionContext<>(null, job));

        assertNotNull(out);
        assertNull(out.getStatus()); // status left for later processors
        assertNotNull(out.getCompletedAt());
    }
}
