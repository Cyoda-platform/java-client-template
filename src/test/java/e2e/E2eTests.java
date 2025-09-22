package e2e;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.Application;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.test.annotation.DirtiesContext;

import static com.java_template.common.config.Config.CYODA_CLIENT_ID;
import static com.java_template.common.config.Config.CYODA_CLIENT_SECRET;

record TestScript(
        String scenario,
        Map<String, Object> variables,
        List<Step> steps
) {
    @Override
    public String toString() {
        return scenario;
    }
}

record Step(
        String name,
        String command,
        Map<String, ?> arguments,
        String expectError
) {}

@DirtiesContext
@SpringBootTest(classes = {Application.class, E2eTestConfig.class})
public class E2eTests {

    private static final Logger LOG = LoggerFactory.getLogger(E2eTests.class);

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PrizeTestProcessor prizeTestProcessor;

    @Autowired
    private Map<String, TestCommand> commands;

    private final HashMap<String, Set<Integer>> usedModels = new HashMap<>();

    @AfterEach
    void cleanup() {
        for (final var nameToVersions : usedModels.entrySet()) {
            final var modelName = nameToVersions.getKey();
            for (final var modelVersion : nameToVersions.getValue()) {
                final var modelSpec = new ModelSpec();
                modelSpec.setName(modelName);
                modelSpec.setVersion(modelVersion);
                entityService.deleteAll(modelSpec);
            }
        }
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("testcasesProvide")
    void tests(final TestScript testScript) {
        final var context = new HashMap<>(testScript.variables());

        if (context.containsKey("modelName")) {
            final var modelName = (String) context.get("modelName");
            final var modelVersion = (Integer) context.get("modelVersion");

            usedModels.computeIfAbsent(modelName, name -> new HashSet<>());
            usedModels.get(modelName).add(modelVersion);
        }

        LOG.info("*** TEST:\t\t| {} | Started ***", testScript.scenario());
        final var commandContext = new CommandContext(
                context,
                objectMapper,
                entityService,
                prizeTestProcessor,
                CYODA_CLIENT_ID,
                CYODA_CLIENT_SECRET
        );
        for (final var step : testScript.steps()) {
            LOG.info("--- STEP:\t\t| {} | Started ---", step.name());
            var args = (Map<?, ?>) resolvePlaceholders(step.arguments(), context);
            args = args != null ? args : new HashMap<>();

            Object result = null;

            final var command = commands.get(step.command());
            if (command == null) {
                Assertions.fail("Unknow command: " + step.command());
            }

            if (step.expectError() == null) {
                result = command.execute(args, commandContext);
            } else {
                try {
                    result = command.execute(args, commandContext);
                } catch (final Exception e) {
                    Assertions.assertTrue(
                            e.getCause().getMessage().contains(step.expectError()),
                            "Expected that error contains: " + step.expectError() + "\nError: " + e
                    );
                }
            }

            LOG.info("=== COMMAND:\t| {} | Finished ===", step.command());

            context.put(
                    step.name(),
                    result instanceof EntityWithMetadata<?>
                            ? objectMapper.convertValue(result, new TypeReference<Map<String, Object>>() {})
                            : result
            );
            LOG.info("--- STEP:\t\t| {} | Finished ---", step.name());
        }
        LOG.info("*** Test:\t\t| {} | Finished ***", testScript.scenario());
    }

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private Object resolvePlaceholders(final Object data, final Map<String, ?> context) {
        if (data == null) {
            return null;
        }
        return switch (data) {
            case String s -> {
                final var matcher = PLACEHOLDER_PATTERN.matcher(s);
                if (matcher.matches()) {
                    final var path = matcher.group(1);
                    yield lookupInContext(path, context);
                }
                yield matcher.replaceAll(matchResult -> {
                    var path = matchResult.group(1);
                    var value = lookupInContext(path, context);
                    return value != null ? value.toString() : matchResult.group(0);
                });
            }
            case Map<?, ?> map -> map.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> resolvePlaceholders(entry.getValue(), context)
                    ));
            case List<?> list -> list.stream()
                    .map(item -> resolvePlaceholders(item, context))
                    .toList();
            default -> data;
        };
    }

    private Object lookupInContext(final String path, final Map<String, ?> context) {
        final var parts = path.split("\\.");
        Object currentValue = context;
        for (var part : parts) {
            if ("variables".equals(part) && currentValue == context) {
                continue;
            }

            if (currentValue instanceof Map<?, ?> currentMap) {
                currentValue = currentMap.get(part);
                if (currentValue == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return currentValue;
    }

    static Stream<TestScript> testcasesProvide() throws URISyntaxException {
        final var mapper = new ObjectMapper(new YAMLFactory());
        return Arrays.stream(new File(E2eTests.class.getResource("/testcases").toURI()).listFiles())
                .filter(File::isFile)
                .filter(it -> it.getName().endsWith(".yaml") || it.getName().endsWith(".yml"))
                .map(it -> {
                    try {
                        return mapper.readValue(it, TestScript.class);
                    } catch (IOException e) {
                        LOG.warn("Can't process test file: '{}'", it.getName(), e);
                        return null;
                    }
                }).filter(Objects::nonNull);
    }

}
