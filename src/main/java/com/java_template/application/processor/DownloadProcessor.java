package com.java_template.application.processor;

import com.java_template.application.entity.dataingestjob.version_1.DataIngestJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class DownloadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DownloadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public DownloadProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataIngestJob download for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataIngestJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(DataIngestJob entity) {
        return entity != null && entity.isValid();
    }

    private DataIngestJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataIngestJob> context) {
        DataIngestJob job = context.entity();
        try {
            logger.info("DownloadProcessor starting for jobTechnicalId={}", job.getTechnicalId());
            // idempotent mark
            job.setStatus("DOWNLOADING");

            String sourceUrl = job.getSource_url();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                job.setStatus("FAILED");
                logger.warn("Job {} missing source_url during download", job.getTechnicalId());
                return job;
            }

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .timeout(java.time.Duration.ofSeconds(20))
                .GET()
                .build();

            HttpResponse<byte[]> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException | InterruptedException e) {
                logger.warn("Transient network error downloading job {}: {}", job.getTechnicalId(), e.getMessage());
                // Keep in DOWNLOADING to allow retries; emit failure via status for visibility
                job.setStatus("DOWNLOADING");
                return job;
            }

            int status = resp.statusCode();
            if (status < 200 || status >= 300) {
                logger.warn("Download failed for job {}: HTTP {}", job.getTechnicalId(), status);
                job.setStatus("FAILED");
                return job;
            }

            byte[] body = resp.body();
            if (body == null || body.length == 0) {
                logger.warn("Downloaded payload empty for job {}", job.getTechnicalId());
                job.setStatus("FAILED");
                return job;
            }

            // Store snapshot as a separate entity type 'Snapshot' is not defined. Persist raw CSV under AnalysisReport.summary_metrics is not appropriate.
            // Instead, we rely on AnalyzeProcessor to fetch the same URL again (idempotent) to compute report. Mark as DOWNLOADED via status transition.
            job.setStatus("ANALYZING");
            logger.info("DownloadProcessor completed for job {} - moving to ANALYZING", job.getTechnicalId());
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error while downloading job {}: {}", job.getTechnicalId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            return job;
        }
    }
}
