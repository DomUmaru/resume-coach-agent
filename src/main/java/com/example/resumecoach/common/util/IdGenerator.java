package com.example.resumecoach.common.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 中文说明：轻量 ID 生成工具。
 * 策略：Demo 阶段使用时间戳 + 随机串，兼顾可读性与冲突概率。
 */
public final class IdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private IdGenerator() {
    }

    public static String generate(String prefix) {
        String ts = LocalDateTime.now().format(FORMATTER);
        String tail = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return prefix + "_" + ts + "_" + tail;
    }
}

