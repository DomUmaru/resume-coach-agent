package com.example.resumecoach.agent.orchestrator;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.agent.model.ToolSelectionDecision;
import com.example.resumecoach.agent.skill.AnswerSkill;
import com.example.resumecoach.agent.skill.IntentSkill;
import com.example.resumecoach.agent.skill.RetrievalSkill;
import com.example.resumecoach.agent.tool.ResumeQaTool;
import com.example.resumecoach.agent.tool.RetrieveResumeContextTool;
import com.example.resumecoach.agent.tool.StarRewriteTool;
import com.example.resumecoach.agent.tool.ToolNames;
import com.example.resumecoach.ai.service.LlmService;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.rag.context.Citation;
import com.example.resumecoach.rag.guardrail.CitationVerifierService;
import com.example.resumecoach.rag.guardrail.NoEvidencePolicyService;
import com.example.resumecoach.resume.model.enumtype.ChunkType;
import com.example.resumecoach.resume.model.enumtype.SectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    private RetrieveResumeContextTool retrieveResumeContextTool;
    @Mock
    private StarRewriteTool starRewriteTool;
    @Mock
    private ResumeQaTool resumeQaTool;
    @Mock
    private CitationVerifierService citationVerifierService;
    @Mock
    private NoEvidencePolicyService noEvidencePolicyService;
    @Mock
    private LlmService llmService;

    private AgentOrchestrator agentOrchestrator;

    @BeforeEach
    void setUp() {
        agentOrchestrator = new AgentOrchestrator(
                new IntentSkill(),
                new RetrievalSkill(),
                new AnswerSkill(),
                retrieveResumeContextTool,
                starRewriteTool,
                resumeQaTool,
                citationVerifierService,
                noEvidencePolicyService,
                llmService);
    }

    @Test
    void shouldRerunRetrieveWithStructuredArguments() {
        ChatStreamRequest request = buildRequest("请结合项目经历回答这个问题", "QA");
        Citation initialCitation = new Citation("child-1", "doc-1", 1, "WORK", 0.80d);
        Citation overrideCitation = new Citation("child-2", "doc-1", 2, "PROJECT", 0.92d);

        when(retrieveResumeContextTool.runWithTrace(anyString(), eq("doc-1"), any(ChatStreamRequest.Options.class)))
                .thenReturn(retrievalExecution("初始证据", List.of(initialCitation), Map.of("rewrittenQuery", "初始证据")))
                .thenReturn(retrievalExecution("限定后的证据", List.of(overrideCitation), Map.of("rewrittenQuery", "限定后的证据")));
        when(llmService.chooseTool(eq("QA"), eq("请结合项目经历回答这个问题"), eq(true)))
                .thenReturn(new ToolSelectionDecision(
                        ToolNames.RETRIEVE,
                        new LinkedHashMap<>(Map.of(
                                "query", "Java 项目",
                                "section", "PROJECT",
                                "page", 2,
                                "chunkType", "CHILD")),
                        validation(ToolNames.RETRIEVE),
                        0.95d,
                        "model"));
        when(citationVerifierService.verify(anyString(), anyString(), any()))
                .thenReturn(new CitationVerifierService.VerificationResult(true, 1.0d, "ok"));

        AgentOrchestrator.AgentResult result = agentOrchestrator.handle(request);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatStreamRequest.Options> optionsCaptor = ArgumentCaptor.forClass(ChatStreamRequest.Options.class);
        verify(retrieveResumeContextTool, times(2))
                .runWithTrace(queryCaptor.capture(), eq("doc-1"), optionsCaptor.capture());

        assertThat(queryCaptor.getAllValues().get(1)).isEqualTo("Java 项目");
        ChatStreamRequest.Filter overrideFilter = optionsCaptor.getAllValues().get(1).getFilter();
        assertThat(overrideFilter.getSection()).isEqualTo(SectionType.PROJECT);
        assertThat(overrideFilter.getPage()).isEqualTo(2);
        assertThat(overrideFilter.getChunkType()).isEqualTo(ChunkType.CHILD);
        assertThat(result.getAnswer()).contains("限定后的证据");
        assertThat(result.getCitations()).containsExactly(overrideCitation);
        assertThat(result.getRetrievalTrace()).containsKey("toolArgumentOverride");
    }

    @Test
    void shouldScopeQaRetrievalByFocusSection() {
        ChatStreamRequest request = buildRequest("请介绍一下你的工作经历", "QA");
        Citation initialCitation = new Citation("child-1", "doc-1", 1, "PROJECT", 0.81d);
        Citation scopedCitation = new Citation("child-3", "doc-1", 3, "WORK", 0.95d);

        when(retrieveResumeContextTool.runWithTrace(anyString(), eq("doc-1"), any(ChatStreamRequest.Options.class)))
                .thenReturn(retrievalExecution("初始证据", List.of(initialCitation), Map.of("rewrittenQuery", "初始证据")))
                .thenReturn(retrievalExecution("工作经历证据", List.of(scopedCitation), Map.of("rewrittenQuery", "工作经历证据")));
        when(llmService.chooseTool(eq("QA"), eq("请介绍一下你的工作经历"), eq(true)))
                .thenReturn(new ToolSelectionDecision(
                        ToolNames.RESUME_QA,
                        new LinkedHashMap<>(Map.of(
                                "question", "请介绍一下你的工作经历",
                                "focusSection", "WORK")),
                        validation(ToolNames.RESUME_QA),
                        0.88d,
                        "model"));
        when(resumeQaTool.run("请介绍一下你的工作经历", "doc-1", "工作经历证据", List.of(scopedCitation)))
                .thenReturn(new ToolCallResult(ToolNames.RESUME_QA, "聚焦后的回答", List.of(scopedCitation)));
        when(citationVerifierService.verify(anyString(), anyString(), any()))
                .thenReturn(new CitationVerifierService.VerificationResult(true, 1.0d, "ok"));

        AgentOrchestrator.AgentResult result = agentOrchestrator.handle(request);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatStreamRequest.Options> optionsCaptor = ArgumentCaptor.forClass(ChatStreamRequest.Options.class);
        verify(retrieveResumeContextTool, times(2))
                .runWithTrace(queryCaptor.capture(), eq("doc-1"), optionsCaptor.capture());

        assertThat(queryCaptor.getAllValues().get(1)).isEqualTo("请介绍一下你的工作经历");
        ChatStreamRequest.Filter scopedFilter = optionsCaptor.getAllValues().get(1).getFilter();
        assertThat(scopedFilter.getSection()).isEqualTo(SectionType.WORK);
        assertThat(result.getAnswer()).contains("聚焦后的回答");
        assertThat(result.getCitations()).containsExactly(scopedCitation);
        assertThat(result.getSelectedToolArgumentValidation()).containsEntry("toolName", ToolNames.RESUME_QA);
    }

    private ChatStreamRequest buildRequest(String message, String intentHint) {
        ChatStreamRequest request = new ChatStreamRequest();
        request.setSessionId("session-1");
        request.setUserId("user-1");
        request.setDocId("doc-1");
        request.setMessage(message);
        request.setIntentHint(intentHint);
        request.setOptions(new ChatStreamRequest.Options());
        return request;
    }

    private RetrieveResumeContextTool.RetrievalExecution retrievalExecution(String content,
                                                                            List<Citation> citations,
                                                                            Map<String, Object> trace) {
        return new RetrieveResumeContextTool.RetrievalExecution(
                new ToolCallResult(ToolNames.RETRIEVE, content, citations),
                new LinkedHashMap<>(trace));
    }

    private Map<String, Object> validation(String toolName) {
        return new LinkedHashMap<>(Map.of(
                "source", "model",
                "toolName", toolName,
                "valid", true));
    }
}
