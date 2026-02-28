package com.example.resumecoach.chat.controller;

import com.example.resumecoach.chat.model.dto.ChatHistoryResponse;
import com.example.resumecoach.chat.service.ChatHistoryService;
import com.example.resumecoach.chat.service.ChatStreamService;
import com.example.resumecoach.common.exception.GlobalExceptionHandler;
import com.example.resumecoach.common.trace.TraceContext;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatStreamService chatStreamService;
    @Mock
    private ChatHistoryService chatHistoryService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TraceContext.setTraceId("trace-chat-001");
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatStreamService, chatHistoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldReturnHistoryResponse() throws Exception {
        ChatHistoryResponse response = new ChatHistoryResponse();
        response.setSessionId("session-1");
        response.setMessages(List.of(new ChatHistoryResponse.MessageItem("assistant", "回答内容", List.of())));
        when(chatHistoryService.queryBySessionId("session-1")).thenReturn(response);

        mockMvc.perform(get("/api/chat/history/session-1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").value("trace-chat-001"))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.messages[0].role").value("assistant"))
                .andExpect(jsonPath("$.data.messages[0].content").value("回答内容"));
    }

    @Test
    void shouldRejectInvalidStreamRequest() throws Exception {
        String body = "{}";

                mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.traceId").value("trace-chat-001"));
    }

    @Test
    void shouldReturnSseEmitterForValidStreamRequest() throws Exception {
        when(chatStreamService.stream(any())).thenReturn(new SseEmitter());

        String body = """
                {
                  "sessionId":"session-1",
                  "userId":"user-1",
                  "docId":"doc-1",
                  "message":"请帮我改写这段项目经历"
                }
                """;

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }
}
