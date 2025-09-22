package e2e.command.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import e2e.CommandContext;
import e2e.TestCommand;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static com.java_template.common.config.Config.CYODA_API_URL;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Component("tool.importWorkflow")
public class ImportWorkflow implements TestCommand {


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
                        (s, r) -> Assertions.fail("Expected 2xx, but got: " + r.getStatusCode())
                ).body(JsonNode.class)
                .get("access_token")
                .asText();
    }

    private void importWorkflow(
            final RestClient client,
            final String workflowFileName,
            final String modelName,
            final Integer modelVersion,
            final ObjectMapper objectMapper,
            final String token
    ) throws URISyntaxException, IOException {

        final var workflowJson = objectMapper.readTree(
                Arrays.stream(new File(ImportWorkflow.class.getResource("/workflows").toURI()).listFiles())
                        .filter(it -> it.getName().equals(workflowFileName))
                        .findFirst()
                        .get()
        );

        client.post()
                .uri(URI.create(CYODA_API_URL + "/model/" + modelName + "/" + modelVersion + "/workflow/import"))
                .contentType(APPLICATION_JSON)
                .header("content-type", APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer " + token)
                .body(workflowJson)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        (s, r) -> Assertions.fail("Expected 2xx, but got: " + r.getStatusCode())
                ).body(String.class);
    }

    @Override
    public Object execute(final Map<?, ?> args, final CommandContext commandContext) throws RuntimeException {
        try {
            final var client = RestClient.create();
            final var token = login(client, commandContext.apiUsername(), commandContext.apiPassword());
            importWorkflow(
                    client,
                    (String) args.get("workflowFile"),
                    (String) args.get("modelName"),
                    (Integer) args.get("modelVersion"),
                    commandContext.objectMapper(),
                    token
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
