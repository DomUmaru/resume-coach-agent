package com.example.resumecoach;

import com.example.resumecoach.rag.retrieval.RetrievalTuningProperties;
import com.example.resumecoach.rag.guardrail.GuardrailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 中文说明：系统启动入口。
 * 输入：无。
 * 输出：启动 Spring Boot Web 应用。
 * 策略：当前以最小可运行骨架为目标，后续按模块逐步补齐能力。
 */
@SpringBootApplication
@EnableConfigurationProperties({RetrievalTuningProperties.class, GuardrailProperties.class})
public class ResumeCoachApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeCoachApplication.class, args);
    }
}
