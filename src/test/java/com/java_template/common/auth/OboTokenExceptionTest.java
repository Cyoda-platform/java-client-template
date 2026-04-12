package com.java_template.common.auth;

// ABOUTME: Unit tests for OboTokenException.

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OboTokenExceptionTest {

    @Test
    void isRuntimeException() {
        assertThat(new OboTokenException("msg")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void preservesMessage() {
        assertThat(new OboTokenException("test message").getMessage()).isEqualTo("test message");
    }

    @Test
    void preservesCause() {
        Throwable cause = new IllegalStateException("root");
        assertThat(new OboTokenException("msg", cause).getCause()).isSameAs(cause);
    }
}
