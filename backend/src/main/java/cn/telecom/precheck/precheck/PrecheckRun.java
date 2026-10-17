package cn.telecom.precheck.precheck;

import java.time.Instant;

/** 数据库行；外部 API 通过 View 解析 JSON，不暴露任何未校验模型输出。 */
public record PrecheckRun(String id, String ticketId, String status, String callStatus,
        String promptVersion, String requestedModel, String actualModel, String traceId,
        Instant createdAt, Instant completedAt, Long durationMs, String failureCode,
        @com.fasterxml.jackson.annotation.JsonIgnore String resultJson, @com.fasterxml.jackson.annotation.JsonIgnore String findingsJson, int version) {}
