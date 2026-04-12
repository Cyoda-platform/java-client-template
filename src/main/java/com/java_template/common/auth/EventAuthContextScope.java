package com.java_template.common.auth;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * AutoCloseable scope that restores the SecurityContext captured before event auth context
 * was established. Intended for try-with-resources use in AbstractEventStrategy.
 */
public class EventAuthContextScope implements AutoCloseable {

    private final SecurityContext previousContext;

    EventAuthContextScope(SecurityContext previousContext) {
        this.previousContext = previousContext;
    }

    @Override
    public void close() {
        SecurityContextHolder.setContext(previousContext);
    }
}
