package com.example.resumecoach.rag.guardrail;

import org.springframework.stereotype.Service;

/**
 * 中文说明：无证据场景策略服务。
 * 策略：在证据不足时优先拒绝编造，并引导用户补充信息或调整提问。
 */
@Service
public class NoEvidencePolicyService {

    private final GuardrailProperties guardrailProperties;

    public NoEvidencePolicyService(GuardrailProperties guardrailProperties) {
        this.guardrailProperties = guardrailProperties;
    }

    /**
     * 中文说明：当完全没有有效证据时返回保守回答。
     * @param intent 当前意图
     * @return 面向用户的降级回复
     */
    public String noEvidenceReply(String intent) {
        if (!guardrailProperties.isNoEvidenceRefuse()) {
            return "当前证据不足，我先给出一般性建议：请提供更具体的信息后，我再给你精确版本。";
        }
        if ("REWRITE".equals(intent)) {
            return "我暂时没有检索到足够的简历证据，无法直接改写。请补充该段项目经历的背景、行动和结果。";
        }
        if ("QA".equals(intent) || "MOCK_INTERVIEW".equals(intent)) {
            return "我暂时没有检索到足够证据来回答这个问题。请换个问法，或先补充对应项目/经历内容。";
        }
        return "当前证据不足，建议先上传或补充简历内容后再继续。";
    }

    /**
     * 中文说明：当回答与证据重叠度过低时返回保守提醒。
     * @param overlap 当前答案与证据的重叠度
     * @return 提示用户重新补充信息的回复
     */
    public String weakCitationReply(double overlap) {
        return "当前回答与检索证据匹配度较低（overlap="
                + String.format("%.2f", overlap)
                + "），为避免无依据输出，请补充更具体问题或简历片段后重试。";
    }
}
