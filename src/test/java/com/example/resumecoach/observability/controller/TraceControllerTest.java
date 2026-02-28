package com.example.resumecoach.observability.controller;

import com.example.resumecoach.common.exception.GlobalExceptionHandler;
import com.example.resumecoach.common.trace.TraceContext;
import com.example.resumecoach.observability.model.RagTraceLogItem;
import com.example.resumecoach.observability.model.TraceSummaryResponse;
import com.example.resumecoach.observability.service.RagTraceLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TraceControllerTest {

    @Mock
    private RagTraceLogService ragTraceLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TraceContext.setTraceId("trace-test-001");
        mockMvc = MockMvcBuilders.standaloneSetup(new TraceController(ragTraceLogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldReturnSummaryResponse() throws Exception {
        TraceSummaryResponse response = new TraceSummaryResponse();
        response.setSessionId("session-1");
        response.setTotalRequests(3);
        response.setAvgTotalMs(120.0d);
        when(ragTraceLogService.summaryBySession("session-1")).thenReturn(response);

        mockMvc.perform(get("/api/trace/summary/session-1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").value("trace-test-001"))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.totalRequests").value(3))
                .andExpect(jsonPath("$.data.avgTotalMs").value(120.0d));
    }

    @Test
    void shouldReturnLatestTraceItems() throws Exception {
        RagTraceLogItem item = new RagTraceLogItem();
        item.setTraceId("trace-1");
        item.setSessionId("session-1");
        item.setRawQuery("项目经历");
        when(ragTraceLogService.latestBySession("session-1")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/trace/session-1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].traceId").value("trace-1"))
                .andExpect(jsonPath("$.data[0].rawQuery").value("项目经历"));
    }
}
