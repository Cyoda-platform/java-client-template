package e2e;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.Application;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import e2e.entity.PrizeEntity;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.junit.jupiter.api.Assertions;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

import static com.java_template.common.config.Config.CYODA_API_URL;
import static com.java_template.common.config.Config.CYODA_CLIENT_ID;
import static com.java_template.common.config.Config.CYODA_CLIENT_SECRET;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@DirtiesContext
@CucumberContextConfiguration
@SpringBootTest(classes = { Application.class, E2eTestConfig.class })
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "e2e")
public class GherkinE2eTest {

    @Autowired
    private EntityService entityService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PrizeTestProcessor prizeTestProcessor;

    private PrizeEntity prizeToCreate;
    private List<PrizeEntity> prizeDefinitions;
    private Exception capturedException;
    private List<UUID> createdPrizeIds;
    private List<EntityWithMetadata<PrizeEntity>> retrievedPrizes;
    private Integer deletedCount = 0;

    @After
    public void cleanup() {
        final var modelSpec = new ModelSpec();
        modelSpec.setName("nobel-prize");
        modelSpec.setVersion(1);
        entityService.deleteAll(modelSpec);
    }

    @Given("I have a prize:")
    public void i_have_a_new_prize_definition(DataTable dataTable) {
        final var data = dataTable.asMaps().getFirst();
        this.prizeToCreate = new PrizeEntity(
                data.get("year"),
                data.get("category"),
                data.get("comment"));
    }

    @Given("I have a list of prizes:")
    public void i_have_a_list_of_prizes(DataTable dataTable) {
        this.prizeDefinitions = dataTable.asMaps(String.class, String.class).stream()
                .map(row -> new PrizeEntity(
                        row.get("year"),
                        row.get("category"),
                        row.get("comment")))
                .collect(Collectors.toList());
    }

    private String toBase64(final String str) {
        return new String(Base64.getEncoder().encode(str.getBytes()));
    }

    private String login(final RestClient client, final String username, final String password) {
        return client.post().uri(URI.create(CYODA_API_URL + "/oauth/token"))
                .contentType(APPLICATION_FORM_URLENCODED)
                .header("content-type", APPLICATION_FORM_URLENCODED_VALUE)
                .header("authorization", "Basic " + toBase64(username + ":" + password))
                .body("grant_type=client_credentials")
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        (s, r) -> Assertions.fail("Expected 2xx, but got: " + r.getStatusCode()))
                .body(JsonNode.class)
                .get("access_token")
                .asText();
    }

    private void importWorkflow(
            final RestClient client,
            final String workflowFileName,
            final String modelName,
            final Integer modelVersion,
            final String token) throws URISyntaxException, IOException {

        final var workflowJson = objectMapper.readTree(
                Arrays.stream(new File(this.getClass().getResource("/workflows").toURI()).listFiles())
                        .filter(it -> it.getName().equals(workflowFileName))
                        .findFirst()
                        .get());

        client.post()
                .uri(URI.create(CYODA_API_URL + "/model/" + modelName + "/" + modelVersion + "/workflow/import"))
                .contentType(APPLICATION_JSON)
                .header("content-type", APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer " + token)
                .body(workflowJson)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        (s, r) -> Assertions.fail("Expected 2xx, but got: " + r.getStatusCode()))
                .body(String.class);
    }

    @When("I import workflow from file {string} for model {string} version {int}")
    public void i_have_a_workflow_in_file(String workflowFileName, String modelName, Integer modelVersion)
            throws URISyntaxException,
            IOException {
        final var client = RestClient.create();
        final var token = login(client, CYODA_CLIENT_ID, CYODA_CLIENT_SECRET);
        importWorkflow(client, workflowFileName, modelName, modelVersion, token);
    }

    @When("I create a single prize")
    public void i_create_a_single_prize() {
        final var entities = List.of(prizeToCreate);
        this.createdPrizeIds = entityService.save(entities).stream().map(EntityWithMetadata::getId).toList();
    }

    @When("I get the prize by its ID")
    public void i_get_the_prize_by_its_id() {
        final var modelSpec = new ModelSpec();
        modelSpec.setName("nobel-prize");
        modelSpec.setVersion(1);

        try {
            this.retrievedPrizes = List.of(entityService.getById(createdPrizeIds.getFirst(), modelSpec, PrizeEntity.class));
        } catch (Exception e) {
            this.capturedException = e;
        }
    }

    @Then("Workflow imported successfully")
    public void workflow_imported_successfully() {

    }

    @Then("the prize's year should be {string}")
    public void the_prizes_year_should_be(String expectedYear) {
        assertEquals(expectedYear, retrievedPrizes.getFirst().entity().year);
    }

    @When("I update the prize with transition {string}")
    public void i_update_prize_with_transition(String transitionName) {
        entityService.update(createdPrizeIds.getFirst(), prizeToCreate, transitionName);
    }

    @Then("Awaits processor is triggered")
    public void awaits_processor_triggered() {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> Assertions.assertTrue(prizeTestProcessor.isProcessTriggered()));
    }

    @When("I update the prize with the year {string} and transition {string}")
    public void i_update_the_prize_with_the_year(String newYear, String transitionsName) {
        final var updatedPrize = new PrizeEntity(newYear, prizeToCreate.category, prizeToCreate.comment);
        entityService.update(createdPrizeIds.getFirst(), updatedPrize, transitionsName);
    }

    @Then("the update should be successful")
    public void the_update_should_be_successful() {
    }

    @When("I delete the prize by its ID")
    public void i_delete_the_prize_by_its_id() {
        entityService.deleteById(createdPrizeIds.getFirst());
    }

    @Then("the deletion should be successful")
    public void the_deletion_should_be_successful() {
    }

    @When("I fetching of models {string} version {int} by condition:")
    public void i_fetching_by_condition(String modelName, Integer modelVersion, String conditionJson)
            throws JsonProcessingException {
        final var condition = objectMapper.readValue(conditionJson, GroupCondition.class);
        final var modelSpec = new ModelSpec();
        modelSpec.setName(modelName);
        modelSpec.setVersion(modelVersion);
        this.retrievedPrizes = entityService.search(modelSpec, condition, PrizeEntity.class);
    }

    @When("I create the prizes in bulk")
    public void i_create_the_prizes_in_bulk() throws Exception {
        this.createdPrizeIds = entityService.save(prizeDefinitions).stream().map(EntityWithMetadata::getId).toList();
    }

    @Then("{int} prizes should be created successfully")
    public void prizes_should_be_created_successfully(Integer expectedCount) {
        assertNotNull(createdPrizeIds);
        assertEquals(expectedCount, createdPrizeIds.size());
    }

    @When("I get all of model {string} version {int}")
    public void i_get_all_prizes_for_the_model(String modelName, Integer modelVersion) throws Exception {
        final var modelSpec = new ModelSpec();
        modelSpec.setName(modelName);
        modelSpec.setVersion(modelVersion);
        this.retrievedPrizes = entityService.findAll(
                modelSpec,
                PrizeEntity.class);
    }

    @When("I delete all of model {string} version {int}")
    public void i_delete_all_of_model(String modelName, Integer modelVersion) {
        final var modelSpec = new ModelSpec();
        modelSpec.setName(modelName);
        modelSpec.setVersion(modelVersion);
        this.deletedCount = this.entityService.deleteAll(modelSpec);
    }

    @Then("{int} prizes were deleted")
    public void count_of_prizes_were_deleted(Integer count) {
        assertEquals(count, this.deletedCount);
    }

    @Then("returned list of {int} prizes")
    public void returned_list_of_prizes(Integer count) {
        assertNotNull(retrievedPrizes);
        assertEquals(count, retrievedPrizes.size());
    }

    @Then("the prize is not found")
    public void the_prize_is_not_found() {
        assertNotNull(capturedException, "An exception was expected but not thrown.");
        assertInstanceOf(CompletionException.class, capturedException, "Exception should be from a future.");

        assertNotNull(capturedException.getCause(), "The ExecutionException should have a cause.");
        System.out
                .println("Successfully verified that getting a deleted item fails with: " + capturedException.getCause()
                        .getClass()
                        .getSimpleName());
    }
}