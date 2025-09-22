package e2e.command.asserts;

import e2e.CommandContext;
import e2e.TestCommand;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.springframework.stereotype.Component;

@Component("assert.isNotNull")
public class IsNotNull implements TestCommand {
    @Override
    public Object execute(final Map<?, ?> args, final CommandContext commandContext) {
        Assertions.assertNotNull(args.get("actual"));
        return null;
    }
}
