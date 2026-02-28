const els = {
  userId: document.getElementById("user-id"),
  sessionId: document.getElementById("session-id"),
  docId: document.getElementById("doc-id"),
  intentHint: document.getElementById("intent-hint"),
  docName: document.getElementById("doc-name"),
  resumeFile: document.getElementById("resume-file"),
  uploadForm: document.getElementById("upload-form"),
  uploadStatus: document.getElementById("upload-status"),
  uploadResult: document.getElementById("upload-result"),
  enableRewrite: document.getElementById("enable-rewrite"),
  enableMulti: document.getElementById("enable-multi"),
  enableRerank: document.getElementById("enable-rerank"),
  enableVector: document.getElementById("enable-vector"),
  filterSection: document.getElementById("filter-section"),
  filterPage: document.getElementById("filter-page"),
  filterChunk: document.getElementById("filter-chunk"),
  message: document.getElementById("message"),
  timeline: document.getElementById("timeline"),
  assistantAnswer: document.getElementById("assistant-answer"),
  citations: document.getElementById("citations"),
  toolCall: document.getElementById("tool-call"),
  traceSummary: document.getElementById("trace-summary"),
  evalOutput: document.getElementById("eval-output"),
  reportsList: document.getElementById("reports-list"),
  timelineItemTemplate: document.getElementById("timeline-item-template"),
};

const state = {
  reports: [],
};

seedDefaults();
bindEvents();

function bindEvents() {
  document.getElementById("seed-session").addEventListener("click", seedDefaults);
  els.uploadForm.addEventListener("submit", onUpload);
  document.getElementById("send-chat").addEventListener("click", onChat);
  document.getElementById("load-history").addEventListener("click", onLoadHistory);
  document.getElementById("load-summary").addEventListener("click", onLoadSummary);
  document.getElementById("load-traces").addEventListener("click", onLoadTraces);
  document.getElementById("run-eval").addEventListener("click", onRunEval);
  document.getElementById("load-reports").addEventListener("click", onLoadReports);
  document.getElementById("compare-reports").addEventListener("click", onCompareReports);
}

function seedDefaults() {
  const stamp = new Date().toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
  els.userId.value = els.userId.value || "user_demo";
  els.sessionId.value = `session_${stamp}`;
  els.message.value = els.message.value || "请根据我的项目经历，生成一段适合面试的回答";
}

async function onUpload(event) {
  event.preventDefault();
  const file = els.resumeFile.files[0];
  if (!file) {
    setStatus("请先选择 PDF 文件", true);
    return;
  }
  if (!els.userId.value.trim()) {
    setStatus("请先填写 User ID", true);
    return;
  }

  const formData = new FormData();
  formData.append("file", file);
  formData.append("userId", els.userId.value.trim());
  if (els.docName.value.trim()) {
    formData.append("docName", els.docName.value.trim());
  }

  setStatus("上传中...");
  writeJson(els.uploadResult, { status: "uploading" });
  try {
    const response = await fetch("/api/resume/upload", {
      method: "POST",
      body: formData,
    });
    const json = await response.json();
    writeJson(els.uploadResult, json);
    if (!response.ok || json.code !== 0) {
      setStatus(json.message || "上传失败", true);
      return;
    }
    els.docId.value = json.data.docId || "";
    setStatus("上传完成");
  } catch (error) {
    setStatus(error.message || "上传失败", true);
    writeJson(els.uploadResult, { error: error.message });
  }
}

async function onChat() {
  clearChatPanels();
  const payload = buildChatPayload();
  if (!payload) {
    return;
  }

  appendTimeline("request", payload);
  const response = await fetch("/api/chat/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok || !response.body) {
    const text = await response.text();
    appendTimeline("error", text);
    return;
  }

  await readSseStream(response.body);
}

async function readSseStream(stream) {
  const reader = stream.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const chunks = buffer.split("\n\n");
    buffer = chunks.pop() || "";
    chunks.forEach(processSseChunk);
  }

  if (buffer.trim()) {
    processSseChunk(buffer);
  }
}

function processSseChunk(chunk) {
  const lines = chunk.split("\n");
  let eventName = "message";
  const dataLines = [];

  lines.forEach((line) => {
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trim());
    }
  });

  const dataText = dataLines.join("\n");
  const payload = parseMaybeJson(dataText);
  appendTimeline(eventName, payload);

  if (eventName === "token") {
    const token = typeof payload === "object" && payload ? payload.content || "" : String(payload || "");
    els.assistantAnswer.textContent += token;
  } else if (eventName === "citation") {
    writeJson(els.citations, payload);
  } else if (eventName === "tool_call") {
    writeJson(els.toolCall, payload);
  } else if (eventName === "done") {
    onLoadSummary();
    onLoadReports();
  }
}

async function onLoadHistory() {
  const sessionId = requireValue(els.sessionId, "请先填写 Session ID");
  if (!sessionId) {
    return;
  }
  const json = await fetchJson(`/api/chat/history/${encodeURIComponent(sessionId)}`);
  writeJson(els.evalOutput, json);
}

async function onLoadSummary() {
  const sessionId = requireValue(els.sessionId, "请先填写 Session ID");
  if (!sessionId) {
    return;
  }
  const json = await fetchJson(`/api/trace/summary/${encodeURIComponent(sessionId)}`);
  writeJson(els.traceSummary, json);
}

async function onLoadTraces() {
  const sessionId = requireValue(els.sessionId, "请先填写 Session ID");
  if (!sessionId) {
    return;
  }
  const json = await fetchJson(`/api/trace/${encodeURIComponent(sessionId)}`);
  writeJson(els.traceSummary, json);
}

async function onRunEval() {
  const docId = requireValue(els.docId, "请先上传简历或填写 Doc ID");
  if (!docId) {
    return;
  }
  const json = await fetchJson("/api/eval/offline", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ docId }),
  });
  writeJson(els.evalOutput, json);
  await onLoadReports();
}

async function onLoadReports() {
  const docId = requireValue(els.docId, "请先上传简历或填写 Doc ID");
  if (!docId) {
    return;
  }
  const json = await fetchJson(`/api/eval/reports/${encodeURIComponent(docId)}`);
  writeJson(els.evalOutput, json);
  state.reports = json.data || [];
  renderReports(state.reports);
}

async function onCompareReports() {
  const selected = Array.from(document.querySelectorAll("input[name='report-select']:checked"))
    .map((input) => input.value);
  if (selected.length !== 2) {
    writeJson(els.evalOutput, { message: "请选择两条 report 再 compare" });
    return;
  }

  const json = await fetchJson("/api/eval/compare", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reportIdA: selected[0], reportIdB: selected[1] }),
  });
  writeJson(els.evalOutput, json);
}

function renderReports(reports) {
  els.reportsList.innerHTML = "";
  if (!reports.length) {
    els.reportsList.innerHTML = "<div class='report-item'><small>暂无报告</small></div>";
    return;
  }

  reports.forEach((report) => {
    const item = document.createElement("article");
    item.className = "report-item";
    item.innerHTML = `
      <header>
        <strong>${escapeHtml(report.reportId || "unknown")}</strong>
        <small>${escapeHtml(report.strategyVersion || "-")}</small>
      </header>
      <small>Hit@K=${safeNum(report.avgHitAtK)} | Recall@K=${safeNum(report.avgRecallAtK)} | nDCG=${safeNum(report.avgNdcgAtK)}</small>
      <div class="report-selector">
        <label><input type="checkbox" name="report-select" value="${escapeAttr(report.reportId || "")}"> 选择对比</label>
        <small>${escapeHtml(report.createdAt || "")}</small>
      </div>
    `;
    els.reportsList.appendChild(item);
  });
}

function buildChatPayload() {
  const sessionId = requireValue(els.sessionId, "请先填写 Session ID");
  const userId = requireValue(els.userId, "请先填写 User ID");
  const docId = requireValue(els.docId, "请先上传简历或填写 Doc ID");
  const message = requireValue(els.message, "请输入消息");
  if (!sessionId || !userId || !docId || !message) {
    return null;
  }

  const filter = {};
  if (els.filterSection.value) {
    filter.section = els.filterSection.value;
  }
  if (els.filterPage.value) {
    filter.page = Number(els.filterPage.value);
  }
  if (els.filterChunk.value) {
    filter.chunkType = els.filterChunk.value;
  }

  return {
    sessionId,
    userId,
    docId,
    message,
    intentHint: els.intentHint.value || null,
    options: {
      enableRewrite: els.enableRewrite.checked,
      enableMultiQuery: els.enableMulti.checked,
      enableRerank: els.enableRerank.checked,
      enableVector: els.enableVector.checked,
      filter: Object.keys(filter).length ? filter : null,
    },
  };
}

function appendTimeline(eventName, payload) {
  const node = els.timelineItemTemplate.content.firstElementChild.cloneNode(true);
  node.querySelector(".timeline-event").textContent = eventName;
  node.querySelector(".timeline-payload").textContent = formatPayload(payload);
  els.timeline.prepend(node);
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  const text = await response.text();
  const json = parseMaybeJson(text);
  if (!response.ok) {
    throw new Error((json && json.message) || text || "request failed");
  }
  return json;
}

function clearChatPanels() {
  els.timeline.innerHTML = "";
  els.assistantAnswer.textContent = "";
  els.citations.textContent = "";
  els.toolCall.textContent = "";
}

function requireValue(element, message) {
  const value = element.value.trim();
  if (!value) {
    writeJson(els.evalOutput, { message });
    element.focus();
    return "";
  }
  return value;
}

function parseMaybeJson(value) {
  if (!value) {
    return "";
  }
  try {
    return JSON.parse(value);
  } catch (error) {
    return value;
  }
}

function writeJson(element, payload) {
  element.textContent = formatPayload(payload);
}

function formatPayload(payload) {
  if (typeof payload === "string") {
    return payload;
  }
  try {
    return JSON.stringify(payload, null, 2);
  } catch (error) {
    return String(payload);
  }
}

function setStatus(text, isError = false) {
  els.uploadStatus.textContent = text;
  els.uploadStatus.classList.toggle("muted", !!isError);
}

function safeNum(value) {
  return typeof value === "number" ? value.toFixed(4) : "-";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function escapeAttr(value) {
  return escapeHtml(value).replaceAll("'", "&#39;");
}
