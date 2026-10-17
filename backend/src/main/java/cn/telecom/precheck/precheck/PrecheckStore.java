package cn.telecom.precheck.precheck;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class PrecheckStore {
    private final PrecheckMapper mapper;
    public PrecheckStore(PrecheckMapper mapper) { this.mapper = mapper; }
    @Transactional
    public void begin(String id, String ticketId, String model, String traceId, String contextJson) {
        mapper.insert(id, ticketId, PromptCatalog.VERSION, model, traceId);
        mapper.snapshot(id, contextJson);
    }
    @Transactional
    public void finish(String id, String callStatus, String actualModel, long durationMs,
                       String failureCode, String resultJson, String findingsJson) {
        mapper.finish(id, callStatus, actualModel, durationMs, failureCode, resultJson, findingsJson);
    }
}
