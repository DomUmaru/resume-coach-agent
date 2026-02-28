package com.example.resumecoach.eval.controller;

import com.example.resumecoach.common.exception.GlobalExceptionHandler;
import com.example.resumecoach.common.trace.TraceContext;
import com.example.resumecoach.eval.model.EvalCompareResponse;
import com.example.resumecoach.eval.model.EvalReportItem;
import com.example.resumecoach.eval.model.EvalSummaryResponse;
import com.example.resumecoach.eval.service.OfflineEvalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EvalControllerTest {

    @Mock
    private OfflineEvalService offlineEvalService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TraceContext.setTraceId("trace-eval-001");
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new EvalController(offlineEvalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldReturnOfflineEvalSummary() throws Exception {
        EvalSummaryResponse summary = new EvalSummaryResponse();
        summary.setTotalCases(3);
        summary.setAvgRecallAtK(0.66d);
        when(offlineEvalService.evaluate("doc-1")).thenReturn(summary);

        mockMvc.perform(post("/api/eval/offline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docId\":\"doc-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").value("trace-eval-001"))
                .andExpect(jsonPath("$.data.totalCases").value(3))
                .andExpect(jsonPath("$.data.avgRecallAtK").value(0.66d));
    }

    @Test
    void shouldReturnLatestReports() throws Exception {
        EvalReportItem report = new EvalReportItem("report-1", "doc-1", "v1", 3, 0.9d, 0.8d, 0.85d, 0.82d, 0.88d, "2026-02-28T11:00:00");
        when(offlineEvalService.latestReports("doc-1")).thenReturn(List.of(report));

        mockMvc.perform(get("/api/eval/reports/doc-1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].reportId").value("report-1"))
                .andExpect(jsonPath("$.data[0].avgNdcgAtK").value(0.85d));
    }

    @Test
    void shouldReturnCompareResult() throws Exception {
        EvalCompareResponse response = new EvalCompareResponse();
        response.setWinner("A");
        response.setSummary("综合得分 A 更优");
        when(offlineEvalService.compare("report-a", "report-b")).thenReturn(response);

        mockMvc.perform(post("/api/eval/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportIdA":"report-a",
                                  "reportIdB":"report-b"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.winner").value("A"))
                .andExpect(jsonPath("$.data.summary").value("综合得分 A 更优"));
    }

    @Test
    void shouldRejectInvalidOfflineEvalRequest() throws Exception {
        mockMvc.perform(post("/api/eval/offline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.traceId").value("trace-eval-001"));
    }
}
