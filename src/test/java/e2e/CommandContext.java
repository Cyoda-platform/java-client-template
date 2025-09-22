package e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import java.util.Map;

public record CommandContext(
        Map<String, Object> testVariables,
        ObjectMapper objectMapper,
        EntityService entityService,
        PrizeTestProcessor prizeTestProcessor,
        String apiUsername,
        String apiPassword
) {}
