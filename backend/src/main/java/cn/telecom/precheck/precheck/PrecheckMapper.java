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

    @Update("UPDATE ai_precheck_evidence_snapshot SET context_json=#{json} WHERE run_id=#{id}")
    int updateSnapshot(@Param("id") String id, @Param("json") String json);

    @Update("""
        UPDATE ai_precheck_evidence_snapshot s JOIN ai_precheck_run r ON r.id=s.run_id
        SET s.context_json=#{json} WHERE s.run_id=#{id} AND r.task_status='RUNNING'
          AND r.attempt_count=#{attempt}
        """)
    int updateTaskSnapshot(@Param("id") String id, @Param("attempt") int attempt, @Param("json") String json);

    @Insert("""
        INSERT INTO ai_precheck_run
        (id,ticket_id,status,call_status,prompt_version,requested_model,trace_id,created_at,
         event_source,event_id,ticket_version,trigger_type,task_status,max_attempts,current_result,updated_at)
        SELECT #{id},#{ticketId},'ANALYZING','PENDING',#{promptVersion},#{model},#{traceId},UTC_TIMESTAMP(6),
          #{source},#{eventId},#{ticketVersion},'EVENT','QUEUED',#{maxAttempts},
          NOT EXISTS(SELECT 1 FROM ai_precheck_run r WHERE r.event_source=#{source}
            AND r.ticket_id=#{ticketId} AND r.ticket_version>#{ticketVersion}),UTC_TIMESTAMP(6)
        ON DUPLICATE KEY UPDATE id=id
        """)
    int insertEvent(@Param("id") String id, @Param("ticketId") String ticketId,
            @Param("promptVersion") String promptVersion, @Param("model") String model,
            @Param("traceId") String traceId, @Param("source") String source,
            @Param("eventId") String eventId, @Param("ticketVersion") long ticketVersion,
            @Param("maxAttempts") int maxAttempts);

    @Update("""
        UPDATE ai_precheck_run SET current_result=FALSE,updated_at=UTC_TIMESTAMP(6)
        WHERE event_source=#{source} AND ticket_id=#{ticketId}
          AND ticket_version<#{ticketVersion} AND current_result=TRUE
        """)
    int supersedeOlder(@Param("source") String source, @Param("ticketId") String ticketId,
                       @Param("ticketVersion") long ticketVersion);

    @Select("SELECT " + COLUMNS + " FROM ai_precheck_run WHERE event_source=#{source} AND ticket_id=#{ticketId} AND ticket_version=#{ticketVersion}")
    PrecheckRun findEventVersion(@Param("source") String source, @Param("ticketId") String ticketId,
                                 @Param("ticketVersion") long ticketVersion);

    @Select("SELECT " + COLUMNS + " FROM ai_precheck_run WHERE event_source=#{source} AND event_id=#{eventId}")
    PrecheckRun findEventId(@Param("source") String source, @Param("eventId") String eventId);

    @Update("""
        UPDATE ai_precheck_run SET task_status='RUNNING',call_status='RUNNING',attempt_count=attempt_count+1,
          started_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6),next_attempt_at=NULL
        WHERE id=#{id} AND task_status IN ('QUEUED','RETRY_WAIT')
          AND (next_attempt_at IS NULL OR next_attempt_at<=UTC_TIMESTAMP(6))
        """)
    int claimTask(String id);

    @Update("""
        UPDATE ai_precheck_run SET status='PENDING_REVIEW',call_status=#{callStatus},task_status=#{taskStatus},
          actual_model=#{actualModel},duration_ms=#{durationMs},failure_code=#{failureCode},
          result_json=#{resultJson},findings_json=#{findingsJson},completed_at=UTC_TIMESTAMP(6),
          updated_at=UTC_TIMESTAMP(6),version=version+1
        WHERE id=#{id} AND task_status='RUNNING' AND attempt_count=#{attempt}
        """)
    int finishTask(@Param("id") String id, @Param("callStatus") String callStatus,
            @Param("taskStatus") String taskStatus, @Param("actualModel") String actualModel,
            @Param("durationMs") long durationMs, @Param("failureCode") String failureCode,
            @Param("resultJson") String resultJson, @Param("findingsJson") String findingsJson,
            @Param("attempt") int attempt);

    @Update("""
        UPDATE ai_precheck_run SET call_status='PENDING',task_status='RETRY_WAIT',failure_code=#{failureCode},
          duration_ms=#{durationMs},next_attempt_at=TIMESTAMPADD(SECOND,#{delaySeconds},UTC_TIMESTAMP(6)),
          updated_at=UTC_TIMESTAMP(6)
        WHERE id=#{id} AND task_status='RUNNING' AND attempt_count=#{attempt}
        """)
    int scheduleRetry(@Param("id") String id, @Param("failureCode") String failureCode,
                      @Param("durationMs") long durationMs, @Param("delaySeconds") int delaySeconds,
                      @Param("attempt") int attempt);

    @Select("""
        SELECT id FROM ai_precheck_run WHERE task_status IN ('QUEUED','RETRY_WAIT')
          AND (next_attempt_at IS NULL OR next_attempt_at<=UTC_TIMESTAMP(6)) ORDER BY created_at LIMIT 100
        """)
    List<String> dueTaskIds();

    @Update("""
        UPDATE ai_precheck_run SET task_status='RETRY_WAIT',call_status='PENDING',failure_code='INTERRUPTED',
          next_attempt_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6)
        WHERE id=#{id} AND task_status='RUNNING' AND attempt_count<max_attempts
          AND updated_at<TIMESTAMPADD(MINUTE,-5,UTC_TIMESTAMP(6))
        """)
    int recoverTaskForRetry(String id);

    @Update("""
        UPDATE ai_precheck_run SET status='PENDING_REVIEW',task_status='FAILED',call_status='FAILED',
          failure_code='INTERRUPTED',completed_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6),version=version+1
        WHERE id=#{id} AND task_status='RUNNING' AND attempt_count>=max_attempts
          AND updated_at<TIMESTAMPADD(MINUTE,-5,UTC_TIMESTAMP(6))
        """)
    int failInterruptedTask(String id);

    @Select("SELECT id FROM ai_precheck_run WHERE task_status='RUNNING' AND updated_at<TIMESTAMPADD(MINUTE,-5,UTC_TIMESTAMP(6)) ORDER BY updated_at LIMIT 100")
    List<String> interruptedTaskIds();

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
            + "failure_code AS failureCode,result_json AS resultJson,findings_json AS findingsJson,version,"
            + "event_source AS eventSource,event_id AS eventId,ticket_version AS ticketVersion,trigger_type AS triggerType,"
            + "task_status AS taskStatus,attempt_count AS attemptCount,max_attempts AS maxAttempts,"
            + "current_result AS currentResult,started_at AS startedAt,next_attempt_at AS nextAttemptAt,updated_at AS updatedAt";
    @Select("SELECT " + COLUMNS + " FROM ai_precheck_run WHERE id=#{id}")
    PrecheckRun find(String id);
    @Select("SELECT " + COLUMNS + " FROM ai_precheck_run WHERE ticket_id=#{ticketId} ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    List<PrecheckRun> history(@Param("ticketId") String ticketId, @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT " + COLUMNS + " FROM ai_precheck_run WHERE ticket_id=#{ticketId} AND current_result=TRUE "
            + "ORDER BY created_at DESC,id DESC LIMIT 1")
    PrecheckRun findCurrent(String ticketId);
    @Select("SELECT context_json FROM ai_precheck_evidence_snapshot WHERE run_id=#{id}")
    String snapshotJson(String id);

    @Update("""
        UPDATE ai_precheck_run SET status='PENDING_REVIEW',call_status='FAILED',failure_code='INTERRUPTED',
          completed_at=UTC_TIMESTAMP(6),version=version+1
        WHERE id=#{id} AND status='ANALYZING' AND trigger_type='MANUAL'
          AND created_at < TIMESTAMPADD(MINUTE,-5,UTC_TIMESTAMP(6))
        """)
    int recoverInterrupted(String id);
    @Select("SELECT id FROM ai_precheck_run WHERE status='ANALYZING' AND trigger_type='MANUAL' AND created_at < TIMESTAMPADD(MINUTE,-5,UTC_TIMESTAMP(6)) ORDER BY created_at LIMIT 100")
    List<String> interruptedIds();

    @Update("""
        UPDATE ai_precheck_run SET status='ANALYZING',call_status='PENDING',task_status='QUEUED',
          attempt_count=0,max_attempts=#{maxAttempts},actual_model=NULL,duration_ms=NULL,failure_code=NULL,
          result_json=NULL,findings_json=NULL,completed_at=NULL,started_at=NULL,next_attempt_at=NULL,
          updated_at=UTC_TIMESTAMP(6),version=version+1
        WHERE id=#{id} AND version=#{expectedVersion} AND trigger_type='EVENT' AND current_result=TRUE
          AND status='PENDING_REVIEW' AND task_status='FAILED'
        """)
    int reauthorizeTask(@Param("id") String id, @Param("expectedVersion") int expectedVersion,
                        @Param("maxAttempts") int maxAttempts);
}
