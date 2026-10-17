package cn.telecom.precheck.context;

/** 宿主整体不可用；部分证据不可用应使用 EvidenceSet.UNAVAILABLE。 */
public class ContextUnavailableException extends RuntimeException {
    public ContextUnavailableException() { super("宿主工单上下文暂不可用"); }
}
