package cn.telecom.precheck.review;
import java.time.Instant;
public record AuditEvent(long id, String runId, String snapshotRunId, String actorId, String action,
        String fromStatus, String toStatus, String outcome, int runVersion, String promptVersion,
        String model, String traceId, Instant occurredAt) {}
