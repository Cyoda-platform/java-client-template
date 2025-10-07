package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.dto.dashboard.*;
import com.java_template.application.service.dashboard.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DashboardController.
 * 
 * <p>Tests HTTP endpoints, JSON serialization, and error handling.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("DashboardController Tests")
class DashboardControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private DashboardService dashboardService;
    
    private DashboardSummaryDTO mockSummary;
    
    @BeforeEach
    void setUp() {
        mockSummary = createMockDashboardSummary();
    }
    
    // ==================== GET /ui/dashboard/summary Tests ====================

    @Test
    @DisplayName("GET /ui/dashboard/summary should return 200 OK with dashboard data")
    void getDashboardSummary_withValidData_returns200Ok() throws Exception {
        // Arrange
        when(dashboardService.getDashboardSummary()).thenReturn(mockSummary);

        // Act & Assert
        mockMvc.perform(get("/ui/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.totalPortfolioValue").value(5000000.00))
            .andExpect(jsonPath("$.activeLoansCount").value(45))
            .andExpect(jsonPath("$.outstandingPrincipal").value(4250000.00))
            .andExpect(jsonPath("$.activeBorrowersCount").value(38))
            .andExpect(jsonPath("$.statusDistribution").exists())
            .andExpect(jsonPath("$.statusDistribution.labels").isArray())
            .andExpect(jsonPath("$.statusDistribution.values").isArray())
            .andExpect(jsonPath("$.portfolioTrend").exists())
            .andExpect(jsonPath("$.portfolioTrend.months").isArray())
            .andExpect(jsonPath("$.portfolioTrend.values").isArray())
            .andExpect(jsonPath("$.aprDistribution").isArray())
            .andExpect(jsonPath("$.monthlyPayments").exists())
            .andExpect(jsonPath("$.monthlyPayments.months").isArray())
            .andExpect(jsonPath("$.monthlyPayments.amounts").isArray());
        
        verify(dashboardService, times(1)).getDashboardSummary();
    }
    
    @Test
    @DisplayName("GET /ui/dashboard/summary should return correct status distribution structure")
    void getDashboardSummary_statusDistribution_hasCorrectStructure() throws Exception {
        // Arrange
        when(dashboardService.getDashboardSummary()).thenReturn(mockSummary);

        // Act & Assert
        mockMvc.perform(get("/ui/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusDistribution.labels[0]").value("active"))
            .andExpect(jsonPath("$.statusDistribution.labels[1]").value("funded"))
            .andExpect(jsonPath("$.statusDistribution.labels[2]").value("matured"))
            .andExpect(jsonPath("$.statusDistribution.values[0]").value(25))
            .andExpect(jsonPath("$.statusDistribution.values[1]").value(15))
            .andExpect(jsonPath("$.statusDistribution.values[2]").value(5));
    }
    
    @Test
    @DisplayName("GET /ui/dashboard/summary should return correct portfolio trend structure")
    void getDashboardSummary_portfolioTrend_hasCorrectStructure() throws Exception {
        // Arrange
        when(dashboardService.getDashboardSummary()).thenReturn(mockSummary);

        // Act & Assert
        mockMvc.perform(get("/ui/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.portfolioTrend.months[0]").value("2024-11"))
            .andExpect(jsonPath("$.portfolioTrend.months[1]").value("2024-12"))
            .andExpect(jsonPath("$.portfolioTrend.months[2]").value("2025-01"))
            .andExpect(jsonPath("$.portfolioTrend.values[0]").value(1500000.00))
            .andExpect(jsonPath("$.portfolioTrend.values[1]").value(1750000.00))
            .andExpect(jsonPath("$.portfolioTrend.values[2]").value(2000000.00));
    }
    
    @Test
    @DisplayName("GET /ui/dashboard/summary should return correct monthly payments structure")
    void getDashboardSummary_monthlyPayments_hasCorrectStructure() throws Exception {
        // Arrange
        when(dashboardService.getDashboardSummary()).thenReturn(mockSummary);

        // Act & Assert
        mockMvc.perform(get("/ui/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.monthlyPayments.months[0]").value("2024-11"))
            .andExpect(jsonPath("$.monthlyPayments.months[1]").value("2024-12"))
            .andExpect(jsonPath("$.monthlyPayments.months[2]").value("2025-01"))
            .andExpect(jsonPath("$.monthlyPayments.amounts[0]").value(125000.00))
            .andExpect(jsonPath("$.monthlyPayments.amounts[1]").value(135000.00))
            .andExpect(jsonPath("$.monthlyPayments.amounts[2]").value(142000.00));
    }
    
    @Test
    @DisplayName("GET /ui/dashboard/summary should return APR distribution array")
    void getDashboardSummary_aprDistribution_isArray() throws Exception {
        // Arrange
        when(dashboardService.getDashboardSummary()).thenReturn(mockSummary);

        // Act & Assert
        mockMvc.perform(get("/ui/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aprDistribution").isArray())
            .andExpect(jsonPath("$.aprDistribution[0]").value(5.5))
            .andExpect(jsonPath("$.aprDistribution[1]").value(6.0))
            .andExpect(jsonPath("$.aprDistribution[2]").value(6.5))
            .andExpect(jsonPath("$.aprDistribution[3]").value(7.0));
    }
    
    @Test
    @DisplayName("GET /ui/dashboard/summary should return 500 when service throws exception")
    void getDashboardSummary_whenServiceThrowsException_returns500() throws Exception {
        // Arrange
        when(dashboardService.getDashboardSummary())
            .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        mockMvc.perform(get("/ui/dashboard/summary"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Failed to retrieve dashboard summary: Database connection failed"));

        verify(dashboardService, times(1)).getDashboardSummary();
    }
    
    @Test
    @DisplayName("GET /ui/dashboard/summary should call service exactly once")
    void getDashboardSummary_callsServiceOnce() throws Exception {
        // Arrange
        when(dashboardService.getDashboardSummary()).thenReturn(mockSummary);

        // Act
        mockMvc.perform(get("/ui/dashboard/summary"))
            .andExpect(status().isOk());

        // Assert
        verify(dashboardService, times(1)).getDashboardSummary();
        verifyNoMoreInteractions(dashboardService);
    }

    // ==================== POST /ui/dashboard/cache/invalidate Tests ====================
    
    @Test
    @DisplayName("POST /ui/dashboard/cache/invalidate should return 204 No Content")
    void invalidateCache_returns204NoContent() throws Exception {
        // Arrange
        doNothing().when(dashboardService).invalidateCache();

        // Act & Assert
        mockMvc.perform(post("/ui/dashboard/cache/invalidate"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(dashboardService, times(1)).invalidateCache();
    }

    @Test
    @DisplayName("POST /ui/dashboard/cache/invalidate should call service invalidateCache method")
    void invalidateCache_callsServiceMethod() throws Exception {
        // Arrange
        doNothing().when(dashboardService).invalidateCache();

        // Act
        mockMvc.perform(post("/ui/dashboard/cache/invalidate"))
            .andExpect(status().isNoContent());

        // Assert
        verify(dashboardService, times(1)).invalidateCache();
        verifyNoMoreInteractions(dashboardService);
    }

    @Test
    @DisplayName("POST /ui/dashboard/cache/invalidate should handle multiple calls")
    void invalidateCache_multipleCallsSucceed() throws Exception {
        // Arrange
        doNothing().when(dashboardService).invalidateCache();

        // Act & Assert
        mockMvc.perform(post("/ui/dashboard/cache/invalidate"))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/ui/dashboard/cache/invalidate"))
            .andExpect(status().isNoContent());

        verify(dashboardService, times(2)).invalidateCache();
    }
    
    // ==================== Helper Methods ====================
    
    private DashboardSummaryDTO createMockDashboardSummary() {
        StatusDistributionDTO statusDistribution = new StatusDistributionDTO(
            Arrays.asList("active", "funded", "matured"),
            Arrays.asList(25, 15, 5)
        );
        
        PortfolioTrendDTO portfolioTrend = new PortfolioTrendDTO(
            Arrays.asList("2024-11", "2024-12", "2025-01"),
            Arrays.asList(
                new BigDecimal("1500000.00"),
                new BigDecimal("1750000.00"),
                new BigDecimal("2000000.00")
            )
        );
        
        MonthlyPaymentsDTO monthlyPayments = new MonthlyPaymentsDTO(
            Arrays.asList("2024-11", "2024-12", "2025-01"),
            Arrays.asList(
                new BigDecimal("125000.00"),
                new BigDecimal("135000.00"),
                new BigDecimal("142000.00")
            )
        );
        
        return new DashboardSummaryDTO(
            new BigDecimal("5000000.00"),  // totalPortfolioValue
            45,                             // activeLoansCount
            new BigDecimal("4250000.00"),  // outstandingPrincipal
            38,                             // activeBorrowersCount
            statusDistribution,
            portfolioTrend,
            Arrays.asList(
                new BigDecimal("5.5"),
                new BigDecimal("6.0"),
                new BigDecimal("6.5"),
                new BigDecimal("7.0")
            ),
            monthlyPayments
        );
    }
}

