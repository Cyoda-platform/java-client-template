package com.java_template.common.util;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ABOUTME: Utility class for extracting clean error messages from Cyoda backend exceptions.
 * Handles the nested exception structure (CompletionException -> StatusRuntimeException)
 * and extracts meaningful error messages without stacktraces.
 *
 * Cyoda exceptions typically follow this pattern:
 * CompletionException: StatusRuntimeException: CANCELLED: Transaction ... was cancelled:
 * Sync process[...] failed: Fail with error code [ERROR_CODE] with message 'External error: <actual message>'
 */
public class CyodaExceptionUtil {

    private static final Logger logger = LoggerFactory.getLogger(CyodaExceptionUtil.class);

    // Pattern to extract message from "with message 'External error: <message>'" or "with message '<message>'"
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("with message '(?:External error: )?([^']+)'");

    // Pattern to extract error code from "Fail with error code [ERROR_CODE]"
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("Fail with error code \\[([^\\]]+)\\]");

    /**
     * Extracts a clean error message from a Cyoda backend exception.
     * Unwraps CompletionException and StatusRuntimeException to find the meaningful error message.
     *
     * @param exception the exception to extract the message from
     * @return clean error message without stacktrace, or the original exception message if pattern not found
     */
    public static String extractErrorMessage(Throwable exception) {
        if (exception == null) {
            return "Unknown error";
        }

        // Unwrap CompletionException
        Throwable cause = exception;
        if (exception instanceof CompletionException && exception.getCause() != null) {
            cause = exception.getCause();
        }

        // Get the message from StatusRuntimeException or the unwrapped exception
        String fullMessage = cause.getMessage();
        if (fullMessage == null) {
            fullMessage = cause.getClass().getSimpleName();
        }

        // Try to extract the meaningful message using the pattern
        Matcher messageMatcher = MESSAGE_PATTERN.matcher(fullMessage);
        if (messageMatcher.find()) {
            String extractedMessage = messageMatcher.group(1);
            logger.debug("Extracted error message: {}", extractedMessage);
            return extractedMessage;
        }

        // If pattern not found, try to extract just the StatusRuntimeException message without the status code
        if (cause instanceof StatusRuntimeException) {
            StatusRuntimeException statusEx = (StatusRuntimeException) cause;
            String description = statusEx.getStatus().getDescription();
            if (description != null && !description.isEmpty()) {
                logger.debug("Using StatusRuntimeException description: {}", description);
                return description;
            }
        }

        // Fallback to the full message
        logger.debug("Using full exception message: {}", fullMessage);
        return fullMessage;
    }

    /**
     * Extracts the error code from a Cyoda backend exception if present.
     *
     * @param exception the exception to extract the error code from
     * @return the error code if found, or null if not found
     */
    public static String extractErrorCode(Throwable exception) {
        if (exception == null) {
            return null;
        }

        // Unwrap CompletionException
        Throwable cause = exception;
        if (exception instanceof CompletionException && exception.getCause() != null) {
            cause = exception.getCause();
        }

        String fullMessage = cause.getMessage();
        if (fullMessage == null) {
            return null;
        }

        // Try to extract the error code using the pattern
        Matcher errorCodeMatcher = ERROR_CODE_PATTERN.matcher(fullMessage);
        if (errorCodeMatcher.find()) {
            String errorCode = errorCodeMatcher.group(1);
            logger.debug("Extracted error code: {}", errorCode);
            return errorCode;
        }

        return null;
    }

    /**
     * Creates a formatted error response message with optional error code.
     *
     * @param exception the exception to format
     * @return formatted error message
     */
    public static String formatErrorResponse(Throwable exception) {
        String errorCode = extractErrorCode(exception);
        String errorMessage = extractErrorMessage(exception);

        if (errorCode != null) {
            return String.format("[%s] %s", errorCode, errorMessage);
        }

        return errorMessage;
    }
}

