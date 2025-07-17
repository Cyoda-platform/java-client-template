package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.prototype.CyodaEntityControllerPrototype.CyodaEnvJob;
import com.java_template.prototype.CyodaEntityControllerPrototype.DeployCyodaEnvRequest;
import com.java_template.prototype.CyodaEntityControllerPrototype.DeployUserAppRequest;
import com.java_template.prototype.CyodaEntityControllerPrototype.UserAppJob;
import com.java_template.prototype.CyodaEntityControllerPrototype.CancelBuildRequest;
import com.java_template.prototype.CyodaEntityControllerPrototype.JobStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/prototype/cyoda-entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, JobStatus> cyodaEnvJobs = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> userAppJobs = new ConcurrentHashMap<>();

    private static final String TEAMCITY_BASE_URL = "https://teamcity.test/app/rest";

    private static final String ENTITY_MODEL_CYODA_ENV_JOB = "CyodaEnvJob";
    private static final String ENTITY_MODEL_USER_APP_JOB = "UserAppJob";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    private String extractToken(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            logger.error("Invalid Authorization header");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid or missing Authorization header");
        }
        return auth.substring(7);
    }

    private HttpHeaders buildAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(MediaType.APPLICATION_JSON.asList());
        return headers;
    }

    @PostMapping(path = "/deploy/cyoda-env", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> deployCyodaEnv(@RequestHeader("Authorization") String authorization,
                                              @RequestBody @Valid DeployCyodaEnvRequest request) {
        logger.info("deployCyodaEnv for user {}", request.getUser_name());
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue";
        String payload = String.format("""
            {
              "buildType":{"id":"KubernetesPipeline_CyodaSaas"},
              "properties":{"property":[
                {"name":"user_defined_keyspace","value":"%s"},
                {"name":"user_defined_namespace","value":"%s"}
              ]}
            }
            """, request.getUser_name(), request.getUser_name());
        var entity = new org.springframework.http.HttpEntity<>(payload, buildAuthHeaders(token));
        try {
            String body = restTemplate.postForEntity(url, entity, String.class).getBody();
            JsonNode node = objectMapper.readTree(body);
            String buildId = node.path("id").asText();
            if (buildId.isEmpty()) throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "No build id");

            CyodaEnvJob job = new CyodaEnvJob();
            job.setBuildId(buildId);
            job.setUserName(request.getUser_name());
            job.setStatus("processing");
            job.setRequestedAt(Instant.now());

            entityService.addItem(ENTITY_MODEL_CYODA_ENV_JOB, ENTITY_VERSION, job);

            cyodaEnvJobs.put(buildId, new JobStatus("processing", Instant.now()));
            return Map.of("build_id", buildId);
        } catch (Exception e) {
            logger.error("Error deployCyodaEnv", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping(path = "/deploy/user-app", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> deployUserApp(@RequestHeader("Authorization") String authorization,
                                             @RequestBody @Valid DeployUserAppRequest request) {
        logger.info("deployUserApp for user {} repo {}", request.getUser_name(), request.getRepository_url());
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue";
        String payload = String.format("""
            {
              "buildType":{"id":"KubernetesPipeline_CyodaSaasUserEnv"},
              "properties":{"property":[
                {"name":"repository_url","value":"%s"},
                {"name":"user_defined_namespace","value":"%s"}
              ]}
            }
            """, request.getRepository_url(), request.getUser_name());
        var entity = new org.springframework.http.HttpEntity<>(payload, buildAuthHeaders(token));
        try {
            String body = restTemplate.postForEntity(url, entity, String.class).getBody();
            JsonNode node = objectMapper.readTree(body);
            String buildId = node.path("id").asText();
            if (buildId.isEmpty()) throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "No build id");

            UserAppJob job = new UserAppJob();
            job.setBuildId(buildId);
            job.setUserName(request.getUser_name());
            job.setRepositoryUrl(request.getRepository_url());
            job.setPublic(request.is_public());
            job.setStatus("processing");
            job.setRequestedAt(Instant.now());

            entityService.addItem(ENTITY_MODEL_USER_APP_JOB, ENTITY_VERSION, job);

            userAppJobs.put(buildId, new JobStatus("processing", Instant.now()));
            return Map.of("build_id", buildId);
        } catch (Exception e) {
            logger.error("Error deployUserApp", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping(path = "/cyoda-env/status/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getCyodaEnvStatus(@RequestHeader("Authorization") String authorization,
                                      @PathVariable("build_id") @NotBlank String buildId) {
        logger.info("getCyodaEnvStatus for {}", buildId);
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue/id:" + buildId;
        return fetchJson(url, token);
    }

    @GetMapping(path = "/cyoda-env/statistics/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getCyodaEnvStatistics(@RequestHeader("Authorization") String authorization,
                                          @PathVariable("build_id") @NotBlank String buildId) {
        logger.info("getCyodaEnvStatistics for {}", buildId);
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/builds/id:" + buildId + "/statistics/";
        return fetchJson(url, token);
    }

    @GetMapping(path = "/user-app/status/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getUserAppStatus(@RequestHeader("Authorization") String authorization,
                                     @PathVariable("build_id") @NotBlank String buildId) {
        logger.info("getUserAppStatus for {}", buildId);
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue/id:" + buildId;
        return fetchJson(url, token);
    }

    @GetMapping(path = "/user-app/statistics/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getUserAppStatistics(@RequestHeader("Authorization") String authorization,
                                         @PathVariable("build_id") @NotBlank String buildId) {
        logger.info("getUserAppStatistics for {}", buildId);
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/builds/id:" + buildId + "/statistics/";
        return fetchJson(url, token);
    }

    @PostMapping(path = "/cancel/user-app/{build_id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> cancelUserAppBuild(@RequestHeader("Authorization") String authorization,
                                                  @PathVariable("build_id") @NotBlank String buildId,
                                                  @RequestBody @Valid CancelBuildRequest request) {
        logger.info("cancelUserAppBuild for {} comment {}", buildId, request.getComment());
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/builds/id:" + buildId;
        String payload = String.format("{\"comment\":\"%s\",\"readdIntoQueue\":%s}", request.getComment(), request.isReaddIntoQueue());
        var entity = new org.springframework.http.HttpEntity<>(payload, buildAuthHeaders(token));
        try {
            restTemplate.postForEntity(url, entity, String.class);

            // Update UserAppJob status and persist update with entityService
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_MODEL_USER_APP_JOB, ENTITY_VERSION,
                    com.java_template.common.util.SearchConditionRequest.group("AND",
                            com.java_template.common.util.Condition.of("$.buildId", "EQUALS", buildId)));
            var items = itemsFuture.join();
            if (items.isEmpty()) {
                logger.warn("UserAppJob not found in entityService: {}", buildId);
            } else {
                com.fasterxml.jackson.databind.node.ObjectNode node = (com.fasterxml.jackson.databind.node.ObjectNode) items.get(0);
                UUID technicalId = UUID.fromString(node.get("technicalId").asText());
                UserAppJob existingJob = objectMapper.convertValue(node, UserAppJob.class);
                existingJob.setStatus("canceled");
                existingJob.setRequestedAt(Instant.now());
                entityService.updateItem(ENTITY_MODEL_USER_APP_JOB, ENTITY_VERSION, technicalId, existingJob);
            }

            userAppJobs.put(buildId, new JobStatus("canceled", Instant.now()));
            return Map.of("result", "success");
        } catch (Exception e) {
            logger.error("Error cancelUserAppBuild", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private JsonNode fetchJson(String url, String token) {
        HttpHeaders headers = buildAuthHeaders(token);
        var entity = new org.springframework.http.HttpEntity<Void>(headers);
        try {
            String body = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class).getBody();
            return objectMapper.readTree(body);
        } catch (Exception e) {
            logger.error("Error fetching JSON from {}", url, e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}