package cn.telecom.precheck.precheck;

/** 只有当前版本的自动失败任务可以由人工授权重新执行。 */
public final class PrecheckRetryConflictException extends RuntimeException {}
