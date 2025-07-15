package com.java_template.common.serializer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ABOUTME: Test class for EvaluationOutcome logical chaining capabilities.
 * Demonstrates and verifies the and(), or(), allOf(), and anyOf() operations.
 */
class EvaluationOutcomeTest {

    /**
     * Test interface for demonstrating short-circuit behavior with mocks.
     */
    interface ValidationChecker {
        EvaluationOutcome check1();
        EvaluationOutcome check2();
        EvaluationOutcome check3();
        EvaluationOutcome check4();
    }

    @Test
    @DisplayName("Should chain outcomes with AND logic - success and success")
    void testAndChaining_SuccessAndSuccess() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.success();
        EvaluationOutcome second = EvaluationOutcome.success();

        // When
        EvaluationOutcome result = first.and(second);

        // Then
        assertTrue(result.isSuccess());
        assertInstanceOf(EvaluationOutcome.Success.class, result);
    }

    @Test
    @DisplayName("Should chain outcomes with AND logic - success and failure")
    void testAndChaining_SuccessAndFailure() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.success();
        EvaluationOutcome second = EvaluationOutcome.Fail.structuralFailure("Second validation failed");

        // When
        EvaluationOutcome result = first.and(second);

        // Then
        assertTrue(result.isFailure());
        assertInstanceOf(EvaluationOutcome.Fail.class, result);
        assertEquals("Second validation failed", ((EvaluationOutcome.Fail) result).getReason());
    }

    @Test
    @DisplayName("Should chain outcomes with AND logic - failure and success")
    void testAndChaining_FailureAndSuccess() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.Fail.businessRuleFailure("First validation failed");
        EvaluationOutcome second = EvaluationOutcome.success();

        // When
        EvaluationOutcome result = first.and(second);

        // Then
        assertTrue(result.isFailure());
        assertInstanceOf(EvaluationOutcome.Fail.class, result);
        assertEquals("First validation failed", ((EvaluationOutcome.Fail) result).getReason());
        assertEquals("BUSINESS_RULE_FAILURE", ((EvaluationOutcome.Fail) result).getCategory());
    }

    @Test
    @DisplayName("Should chain outcomes with AND logic - failure and failure returns first")
    void testAndChaining_FailureAndFailure() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.Fail.structuralFailure("First failure");
        EvaluationOutcome second = EvaluationOutcome.Fail.dataQualityFailure("Second failure");

        // When
        EvaluationOutcome result = first.and(second);

        // Then
        assertTrue(result.isFailure());
        assertEquals("First failure", ((EvaluationOutcome.Fail) result).getReason());
        assertEquals("STRUCTURAL_FAILURE", ((EvaluationOutcome.Fail) result).getCategory());
    }

    @Test
    @DisplayName("Should chain outcomes with OR logic - success or failure")
    void testOrChaining_SuccessOrFailure() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.success();
        EvaluationOutcome second = EvaluationOutcome.Fail.structuralFailure("Second validation failed");

        // When
        EvaluationOutcome result = first.or(second);

        // Then
        assertTrue(result.isSuccess());
        assertInstanceOf(EvaluationOutcome.Success.class, result);
    }

    @Test
    @DisplayName("Should chain outcomes with OR logic - failure or success")
    void testOrChaining_FailureOrSuccess() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.Fail.businessRuleFailure("First validation failed");
        EvaluationOutcome second = EvaluationOutcome.success();

        // When
        EvaluationOutcome result = first.or(second);

        // Then
        assertTrue(result.isSuccess());
        assertInstanceOf(EvaluationOutcome.Success.class, result);
    }

    @Test
    @DisplayName("Should chain outcomes with OR logic - failure or failure returns last")
    void testOrChaining_FailureOrFailure() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.Fail.structuralFailure("First failure");
        EvaluationOutcome second = EvaluationOutcome.Fail.dataQualityFailure("Second failure");

        // When
        EvaluationOutcome result = first.or(second);

        // Then
        assertTrue(result.isFailure());
        assertEquals("Second failure", ((EvaluationOutcome.Fail) result).getReason());
        assertEquals("DATA_QUALITY_FAILURE", ((EvaluationOutcome.Fail) result).getCategory());
    }

    @Test
    @DisplayName("Should use allOf for multiple AND operations")
    void testAllOf_MultipleOutcomes() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.success();
        EvaluationOutcome second = EvaluationOutcome.success();
        EvaluationOutcome third = EvaluationOutcome.Fail.businessRuleFailure("Third failed");
        EvaluationOutcome fourth = EvaluationOutcome.success();

        // When
        EvaluationOutcome result = EvaluationOutcome.allOf(first, second, third, fourth);

        // Then
        assertTrue(result.isFailure());
        assertEquals("Third failed", ((EvaluationOutcome.Fail) result).getReason());
        assertEquals("BUSINESS_RULE_FAILURE", ((EvaluationOutcome.Fail) result).getCategory());
    }

    @Test
    @DisplayName("Should use allOf for all successful outcomes")
    void testAllOf_AllSuccessful() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.success();
        EvaluationOutcome second = EvaluationOutcome.success();
        EvaluationOutcome third = EvaluationOutcome.success();

        // When
        EvaluationOutcome result = EvaluationOutcome.allOf(first, second, third);

        // Then
        assertTrue(result.isSuccess());
        assertInstanceOf(EvaluationOutcome.Success.class, result);
    }

    @Test
    @DisplayName("Should use anyOf for multiple OR operations")
    void testAnyOf_MultipleOutcomes() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.Fail.structuralFailure("First failed");
        EvaluationOutcome second = EvaluationOutcome.success();
        EvaluationOutcome third = EvaluationOutcome.Fail.businessRuleFailure("Third failed");

        // When
        EvaluationOutcome result = EvaluationOutcome.anyOf(first, second, third);

        // Then
        assertTrue(result.isSuccess());
        assertInstanceOf(EvaluationOutcome.Success.class, result);
    }

    @Test
    @DisplayName("Should use anyOf for all failed outcomes")
    void testAnyOf_AllFailed() {
        // Given
        EvaluationOutcome first = EvaluationOutcome.Fail.structuralFailure("First failed");
        EvaluationOutcome second = EvaluationOutcome.Fail.businessRuleFailure("Second failed");
        EvaluationOutcome third = EvaluationOutcome.Fail.dataQualityFailure("Third failed");

        // When
        EvaluationOutcome result = EvaluationOutcome.anyOf(first, second, third);

        // Then
        assertTrue(result.isFailure());
        assertEquals("Third failed", ((EvaluationOutcome.Fail) result).getReason());
        assertEquals("DATA_QUALITY_FAILURE", ((EvaluationOutcome.Fail) result).getCategory());
    }

    @Test
    @DisplayName("Should demonstrate complex chaining scenarios")
    void testComplexChaining() {
        // Given - simulate a complex validation scenario
        EvaluationOutcome structuralCheck = EvaluationOutcome.success();
        EvaluationOutcome businessRuleCheck = EvaluationOutcome.success();
        EvaluationOutcome dataQualityCheck = EvaluationOutcome.Fail.dataQualityFailure("Invalid data format");
        
        // When - chain with AND logic (all must pass)
        EvaluationOutcome strictValidation = structuralCheck
            .and(businessRuleCheck)
            .and(dataQualityCheck);
        
        // When - chain with OR logic (any can pass)
        EvaluationOutcome lenientValidation = structuralCheck
            .or(businessRuleCheck)
            .or(dataQualityCheck);

        // Then
        assertTrue(strictValidation.isFailure()); // Fails because data quality failed
        assertTrue(lenientValidation.isSuccess()); // Succeeds because structural check passed
    }

    @Test
    @DisplayName("Should provide convenience methods for checking outcome type")
    void testConvenienceMethods() {
        // Given
        EvaluationOutcome success = EvaluationOutcome.success();
        EvaluationOutcome failure = EvaluationOutcome.Fail.structuralFailure("Test failure");

        // Then
        assertTrue(success.isSuccess());
        assertFalse(success.isFailure());

        assertFalse(failure.isSuccess());
        assertTrue(failure.isFailure());
    }

    @Test
    @DisplayName("Should short-circuit allOf() with suppliers on first failure")
    void testAllOfShortCircuitsWithSuppliers() {
        // Given - create suppliers that we can verify were called or not
        @SuppressWarnings("unchecked")
        Supplier<EvaluationOutcome> supplier1 = mock(Supplier.class);
        @SuppressWarnings("unchecked")
        Supplier<EvaluationOutcome> supplier2 = mock(Supplier.class);
        @SuppressWarnings("unchecked")
        Supplier<EvaluationOutcome> supplier3 = mock(Supplier.class);
        @SuppressWarnings("unchecked")
        Supplier<EvaluationOutcome> supplier4 = mock(Supplier.class);

        // Configure suppliers
        when(supplier1.get()).thenReturn(EvaluationOutcome.success());
        when(supplier2.get()).thenReturn(EvaluationOutcome.Fail.structuralFailure("Second failed"));
        when(supplier3.get()).thenReturn(EvaluationOutcome.success());
        when(supplier4.get()).thenReturn(EvaluationOutcome.Fail.businessRuleFailure("Fourth failed"));

        // When - call allOf with suppliers (should short-circuit after second failure)
        EvaluationOutcome result = EvaluationOutcome.allOf(
            supplier1,
            supplier2,
            supplier3,
            supplier4
        );

        // Then - should return the first failure (second supplier)
        assertTrue(result.isFailure());
        assertEquals("Second failed", ((EvaluationOutcome.Fail) result).getReason());
        assertEquals("STRUCTURAL_FAILURE", ((EvaluationOutcome.Fail) result).getCategory());

        // Verify short-circuiting: only first two suppliers should be called
        verify(supplier1, times(1)).get();
        verify(supplier2, times(1)).get();
        verify(supplier3, never()).get(); // Should not be called due to short-circuit
        verify(supplier4, never()).get(); // Should not be called due to short-circuit
    }

    @Test
    @DisplayName("Should short-circuit chained and() calls on first failure")
    void testChainedAndShortCircuits() {
        // Given - create a test class that tracks method calls
        ValidationChecker checker = mock(ValidationChecker.class);

        // Configure the checker to return success, then failure
        when(checker.check1()).thenReturn(EvaluationOutcome.success());
        when(checker.check2()).thenReturn(EvaluationOutcome.Fail.structuralFailure("Second failed"));
        when(checker.check3()).thenReturn(EvaluationOutcome.success());
        when(checker.check4()).thenReturn(EvaluationOutcome.Fail.businessRuleFailure("Fourth failed"));

        // When - chain and() calls using a helper method that demonstrates short-circuiting
        EvaluationOutcome result = performChainedAndValidation(checker);

        // Then - should return the second failure
        assertTrue(result.isFailure());
        assertEquals("Second failed", ((EvaluationOutcome.Fail) result).getReason());
        assertEquals("STRUCTURAL_FAILURE", ((EvaluationOutcome.Fail) result).getCategory());

        // Verify short-circuiting: only first two checks should be called
        verify(checker, times(1)).check1();
        verify(checker, times(1)).check2();
        verify(checker, never()).check3(); // Should not be called due to short-circuit
        verify(checker, never()).check4(); // Should not be called due to short-circuit
    }

    // Helper method that demonstrates proper short-circuiting with chained and()
    private EvaluationOutcome performChainedAndValidation(ValidationChecker checker) {
        EvaluationOutcome result = checker.check1();
        if (result.isFailure()) return result;

        result = result.and(checker.check2());
        if (result.isFailure()) return result;

        result = result.and(checker.check3());
        if (result.isFailure()) return result;

        return result.and(checker.check4());
    }

    @Test
    @DisplayName("Should short-circuit anyOf() with suppliers on first success")
    void testAnyOfShortCircuitsWithSuppliers() {
        // Given - create suppliers that we can verify were called or not
        @SuppressWarnings("unchecked")
        Supplier<EvaluationOutcome> supplier1 = mock(Supplier.class);
        @SuppressWarnings("unchecked")
        Supplier<EvaluationOutcome> supplier2 = mock(Supplier.class);
        @SuppressWarnings("unchecked")
        Supplier<EvaluationOutcome> supplier3 = mock(Supplier.class);
        @SuppressWarnings("unchecked")
        Supplier<EvaluationOutcome> supplier4 = mock(Supplier.class);

        // Configure suppliers
        when(supplier1.get()).thenReturn(EvaluationOutcome.Fail.structuralFailure("First failed"));
        when(supplier2.get()).thenReturn(EvaluationOutcome.success());
        when(supplier3.get()).thenReturn(EvaluationOutcome.Fail.businessRuleFailure("Third failed"));
        when(supplier4.get()).thenReturn(EvaluationOutcome.success());

        // When - call anyOf with suppliers (should short-circuit after second success)
        EvaluationOutcome result = EvaluationOutcome.anyOf(
            supplier1,
            supplier2,
            supplier3,
            supplier4
        );

        // Then - should return success from the first successful supplier (second one)
        assertTrue(result.isSuccess());

        // Verify short-circuiting: only first two suppliers should be called
        verify(supplier1, times(1)).get();
        verify(supplier2, times(1)).get();
        verify(supplier3, never()).get(); // Should not be called due to short-circuit
        verify(supplier4, never()).get(); // Should not be called due to short-circuit
    }

    @Test
    @DisplayName("Should short-circuit chained or() calls on first success")
    void testChainedOrShortCircuits() {
        // Given - create a test class that tracks method calls
        ValidationChecker checker = mock(ValidationChecker.class);

        // Configure the checker to return failure, then success
        when(checker.check1()).thenReturn(EvaluationOutcome.Fail.structuralFailure("First failed"));
        when(checker.check2()).thenReturn(EvaluationOutcome.success());
        when(checker.check3()).thenReturn(EvaluationOutcome.Fail.businessRuleFailure("Third failed"));
        when(checker.check4()).thenReturn(EvaluationOutcome.success());

        // When - chain or() calls using a helper method that demonstrates short-circuiting
        EvaluationOutcome result = performChainedOrValidation(checker);

        // Then - should return success from the second check
        assertTrue(result.isSuccess());

        // Verify short-circuiting: only first two checks should be called
        verify(checker, times(1)).check1();
        verify(checker, times(1)).check2();
        verify(checker, never()).check3(); // Should not be called due to short-circuit
        verify(checker, never()).check4(); // Should not be called due to short-circuit
    }

    // Helper method that demonstrates proper short-circuiting with chained or()
    private EvaluationOutcome performChainedOrValidation(ValidationChecker checker) {
        EvaluationOutcome result = checker.check1();
        if (result.isSuccess()) return result;

        result = result.or(checker.check2());
        if (result.isSuccess()) return result;

        result = result.or(checker.check3());
        if (result.isSuccess()) return result;

        return result.or(checker.check4());
    }

    @Test
    @DisplayName("Should demonstrate that allOf convenience overload evaluates all arguments")
    void testAllOfConvenienceOverloadEvaluatesAllArguments() {
        // Given - create a checker that tracks method calls
        ValidationChecker checker = mock(ValidationChecker.class);

        when(checker.check1()).thenReturn(EvaluationOutcome.success());
        when(checker.check2()).thenReturn(EvaluationOutcome.Fail.structuralFailure("Failed"));
        when(checker.check3()).thenReturn(EvaluationOutcome.success());
        when(checker.check4()).thenReturn(EvaluationOutcome.success());

        // When using allOf() convenience overload - all arguments are evaluated before processing
        EvaluationOutcome result = EvaluationOutcome.allOf(
            checker.check1(),
            checker.check2(),
            checker.check3(),
            checker.check4()
        );

        // Then - should return the failure
        assertTrue(result.isFailure());
        assertEquals("Failed", ((EvaluationOutcome.Fail) result).getReason());

        // Verify all methods were called (convenience overload evaluates all arguments)
        verify(checker, times(1)).check1();
        verify(checker, times(1)).check2();
        verify(checker, times(1)).check3();
        verify(checker, times(1)).check4();
    }

    @Test
    @DisplayName("Should demonstrate that anyOf convenience overload evaluates all arguments")
    void testAnyOfConvenienceOverloadEvaluatesAllArguments() {
        // Given - create a checker that tracks method calls
        ValidationChecker checker = mock(ValidationChecker.class);

        when(checker.check1()).thenReturn(EvaluationOutcome.Fail.structuralFailure("First failed"));
        when(checker.check2()).thenReturn(EvaluationOutcome.success());
        when(checker.check3()).thenReturn(EvaluationOutcome.Fail.businessRuleFailure("Third failed"));
        when(checker.check4()).thenReturn(EvaluationOutcome.success());

        // When using anyOf() convenience overload - all arguments are evaluated before processing
        EvaluationOutcome result = EvaluationOutcome.anyOf(
            checker.check1(),
            checker.check2(),
            checker.check3(),
            checker.check4()
        );

        // Then - should return success (from the first successful check)
        assertTrue(result.isSuccess());

        // Verify all methods were called (convenience overload evaluates all arguments)
        verify(checker, times(1)).check1();
        verify(checker, times(1)).check2();
        verify(checker, times(1)).check3();
        verify(checker, times(1)).check4();
    }
}
