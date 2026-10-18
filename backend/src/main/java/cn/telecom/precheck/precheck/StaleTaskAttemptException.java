package cn.telecom.precheck.precheck;

/** 旧执行租约已经被恢复器回收，旧工作线程必须停止写入。 */
final class StaleTaskAttemptException extends RuntimeException {}
