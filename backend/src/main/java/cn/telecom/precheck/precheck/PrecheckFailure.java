package cn.telecom.precheck.precheck;

/** 只保存受控错误码，不保存供应商异常原文或模型未校验输出。 */
public final class PrecheckFailure extends RuntimeException {
    public enum Code { MODEL_NOT_CONFIGURED, MODEL_TIMEOUT, MODEL_ERROR, INVALID_OUTPUT,
        INVALID_EVIDENCE_REFERENCE, INVALID_CONTEXT, INTERRUPTED }
    private final Code code;
    public PrecheckFailure(Code code) { super(code.name()); this.code = code; }
    public Code code() { return code; }
}
