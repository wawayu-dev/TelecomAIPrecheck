package cn.telecom.precheck.precheck;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PrecheckMapper {
    @Insert("""
        INSERT INTO ai_precheck_run
        (id,ticket_id,status,call_status,prompt_version,requested_model,trace_id,created_at)
        VALUES(#{id},#{ticketId},'ANALYZING','RUNNING',#{promptVersion},#{model},#{traceId},UTC_TIMESTAMP(6))
        """)
    void insert(@Param("id") String id, @Param("ticketId") String ticketId,
                @Param("promptVersion") String promptVersion, @Param("model") String model, @Param("traceId") String traceId);

    @Insert("INSERT INTO ai_precheck_evidence_snapshot(run_id,context_json) VALUES(#{id},#{json})")
    void snapshot(@Param("id") String id, @Param("json") String json);

    @Update("""
        UPDATE ai_precheck_run SET status='PENDING_REVIEW',call_status=#{callStatus},
          actual_model=#{actualModel},duration_ms=#{durationMs},failure_code=#{failureCode},
          result_json=#{resultJson},findings_json=#{findingsJson},completed_at=UTC_TIMESTAMP(6),version=version+1
        WHERE id=#{id} AND status='ANALYZING'
        """)
    int finish(@Param("id") String id, @Param("callStatus") String callStatus, @Param("actualModel") String actualModel,
               @Param("durationMs") long durationMs, @Param("failureCode") String failureCode,
               @Param("resultJson") String resultJson, @Param("findingsJson") String findingsJson);

    String COLUMNS = "id,ticket_id AS ticketId,status,call_status AS callStatus,prompt_version AS promptVersion,"
            + "requested_model AS requestedModel,actual_model AS actualModel,trace_id AS traceId,"
            + "created_at AS createdAt,completed_at AS completedAt,duration_ms AS durationMs,"
            + "failure_code AS failureCode,result_json AS resultJson,findings_json AS findingsJson,version";
    @Select("SELECT " + COLUMNS + " FROM ai_precheck_run WHERE id=#{id}")
    PrecheckRun find(String id);
    @Select("SELECT " + COLUMNS + " FROM ai_precheck_run WHERE ticket_id=#{ticketId} ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<PrecheckRun> history(@Param("ticketId") String ticketId, @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT context_json FROM ai_precheck_evidence_snapshot WHERE run_id=#{id}")
    String snapshotJson(String id);

    @Update("""
        UPDATE ai_precheck_run SET status='PENDING_REVIEW',call_status='FAILED',failure_code='INTERRUPTED',
          completed_at=UTC_TIMESTAMP(6),version=version+1
        WHERE status='ANALYZING' AND created_at < TIMESTAMPADD(MINUTE,-5,UTC_TIMESTAMP(6))
        """)
    int recoverInterrupted();
}
