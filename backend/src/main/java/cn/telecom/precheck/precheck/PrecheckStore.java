package cn.telecom.precheck.precheck;

import cn.telecom.precheck.review.AuditMapper;
import cn.telecom.precheck.security.CurrentActor;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Profile("mysql")
public class PrecheckStore {
    private final PrecheckMapper mapper;
    private final AuditMapper audits;
    public PrecheckStore(PrecheckMapper mapper, AuditMapper audits) { this.mapper = mapper; this.audits=audits; }
    @Transactional
    public void begin(String id, String ticketId, String model, String traceId, String contextJson) {
        String actor=CurrentActor.id();
        mapper.insert(id, ticketId, PromptCatalog.VERSION, model, traceId);
        mapper.snapshot(id, contextJson);
        append(id,actor,"PRECHECK_STARTED",null,"COMMITTED",traceId);
    }
    @Transactional
    public void finish(String id, String callStatus, String actualModel, long durationMs,
                       String failureCode, String resultJson, String findingsJson) {
        if(mapper.finish(id, callStatus, actualModel, durationMs, failureCode, resultJson, findingsJson)==1) {
            append(id,"system:model","PRECHECK_"+callStatus,"ANALYZING",callStatus,mapper.find(id).traceId());
        }
    }
    @Transactional
    public void recover(String id) {
        if(mapper.recoverInterrupted(id)==1)
            append(id,"system:recovery","PRECHECK_INTERRUPTED","ANALYZING","FAILED",UUID.randomUUID().toString());
    }
    private void append(String id,String actor,String action,String from,String outcome,String trace) {
        if(audits.append(id,actor,action,from,outcome,trace)!=1) throw new IllegalStateException("Audit write failed");
    }
}
