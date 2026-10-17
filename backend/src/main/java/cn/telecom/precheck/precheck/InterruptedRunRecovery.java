package cn.telecom.precheck.precheck;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

/** 进程意外终止留下的 ANALYZING 记录最终进入人工队列；不重试或重复计费。 */
@Configuration
@Profile("mysql")
@EnableScheduling
public class InterruptedRunRecovery {
    private final PrecheckMapper mapper;
    public InterruptedRunRecovery(PrecheckMapper mapper) { this.mapper = mapper; }
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void recover() { mapper.recoverInterrupted(); }
}
