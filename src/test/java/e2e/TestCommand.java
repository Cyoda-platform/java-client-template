package e2e;

import java.util.Map;

public interface TestCommand {
    Object execute(Map<?,?> args, CommandContext commandContext) throws RuntimeException;
}
