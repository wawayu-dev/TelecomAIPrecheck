package cn.telecom.precheck.review;
import org.apache.ibatis.annotations.*;
@Mapper
public interface ReviewMapper {
    @Update("""
        UPDATE ai_precheck_run SET status='REVIEWED',version=version+1
        WHERE id=#{id} AND status='PENDING_REVIEW' AND call_status IN ('SUCCESS','FAILED') AND version=#{version}
        """)
    int claim(@Param("id") String id,@Param("version") int version);
    @Insert("""
        INSERT INTO ai_precheck_review(run_id,decision,summary,recommendation,reason,actor_id,trace_id,reviewed_at,version)
        VALUES(#{id},#{request.decision},#{request.summary},#{request.recommendation},#{request.reason},#{actor},#{trace},UTC_TIMESTAMP(6),#{version})
        """)
    void insert(@Param("id") String id,@Param("request") ReviewRequest request,@Param("actor") String actor,
                @Param("trace") String trace,@Param("version") int version);
    @Select("""
        SELECT run_id AS runId,decision,summary,recommendation,reason,actor_id AS actorId,trace_id AS traceId,
        reviewed_at AS reviewedAt,version FROM ai_precheck_review WHERE run_id=#{id}
        """)
    Review find(String id);
}
