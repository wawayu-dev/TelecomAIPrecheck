package cn.telecom.precheck.precheck;

public interface PrecheckModel {
    record Reply(String content, String model) {}
    Reply call(String systemPrompt, String userPayload);
    String modelId();
}
