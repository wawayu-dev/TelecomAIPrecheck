package cn.telecom.precheck.review;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface AuditMapper {
    @Insert("""
        INSERT INTO ai_precheck_audit
        (run_id,snapshot_run_id,actor_id,action,from_status,to_status,outcome,run_version,prompt_version,model,trace_id,occurred_at)
        SELECT id,id,#{actor},#{action},#{from},status,#{outcome},version,prompt_version,
        COALESCE(actual_model,requested_model),#{trace},UTC_TIMESTAMP(6) FROM ai_precheck_run WHERE id=#{id}
        """)
    int append(@Param("id") String id,@Param("actor") String actor,@Param("action") String action,
               @Param("from") String from,@Param("outcome") String outcome,@Param("trace") String trace);
    @Select("""
        SELECT id,run_id AS runId,snapshot_run_id AS snapshotRunId,actor_id AS actorId,action,
        from_status AS fromStatus,to_status AS toStatus,outcome,run_version AS runVersion,
        prompt_version AS promptVersion,model,trace_id AS traceId,occurred_at AS occurredAt
        FROM ai_precheck_audit WHERE run_id=#{id} AND id>#{after} ORDER BY id LIMIT #{limit}
        """)
    List<AuditEvent> list(@Param("id") String id,@Param("after") long after,@Param("limit") int limit);
}
