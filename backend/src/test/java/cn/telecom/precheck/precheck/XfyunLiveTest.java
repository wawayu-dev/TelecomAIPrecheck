package cn.telecom.precheck.precheck;

import cn.telecom.precheck.context.TicketContextProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

/** 明确开启才会产生一次真实供应商调用；仅发送合成样本，不伪造验收成功。 */
@org.springframework.security.test.context.support.WithMockUser(username="operator",roles="OPERATOR")
@SpringBootTest(properties="precheck.context-provider=mock")
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named="RUN_XFYUN_LIVE",matches="true")
class XfyunLiveTest {
    @Autowired PrecheckService service;
    @Autowired TicketContextProvider provider;
    @Test void realProviderCompletesAndPersistsOnePrecheck() {
        assertThat(provider.getContext("DEMO-001").synthetic()).isTrue();
        var view=service.start("DEMO-001",UUID.randomUUID().toString());
        assertThat(view.run().callStatus()).as("真实联调结果，失败码=%s",view.run().failureCode()).isEqualTo("SUCCESS");
        assertThat(view.result()).isNotNull();
        assertThat(service.get(view.run().id()).evidenceSnapshot()).isNotNull();
        System.out.printf("XFYUN_LIVE runId=%s model=%s durationMs=%d status=%s%n",
                view.run().id(),view.run().actualModel(),view.run().durationMs(),view.run().callStatus());
    }
}
