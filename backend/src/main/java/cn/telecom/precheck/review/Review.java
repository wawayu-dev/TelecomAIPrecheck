package cn.telecom.precheck.review;
import java.time.Instant;
public record Review(String runId, String decision, String summary, String recommendation, String reason,
        String actorId, String traceId, Instant reviewedAt, int version) {}
