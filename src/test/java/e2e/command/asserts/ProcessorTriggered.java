package e2e.command.asserts;

import e2e.CommandContext;
import e2e.TestCommand;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.springframework.stereotype.Component;

@Component("assert.processorTriggered")
public class ProcessorTriggered implements TestCommand {

    @Override
    public Object execute(final Map<?, ?> args, final CommandContext commandContext) {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> Assertions.assertTrue(commandContext.prizeTestProcessor().isProcessTriggered()));
        return null;
    }
}
