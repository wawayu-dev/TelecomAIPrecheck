package cn.telecom.precheck.precheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"precheck.tasks.max-attempts=2", "precheck.tasks.retry-delay-seconds=0",
        "precheck.tasks.scan-delay-ms=60000", "DEMO_HOST_PASSWORD=host-test-password"})
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS",matches="true")
class AsyncPrecheckIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PrecheckMapper runs;
    @Autowired AsyncPrecheckService async;
    @Autowired PrecheckStore store;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PrecheckModel model;
    long version() { return System.currentTimeMillis() * 1_000 + Math.floorMod(System.nanoTime(), 1_000); }

    @BeforeEach void modelSucceeds() {
        when(model.modelId()).thenReturn("async-test-model");
        when(model.call(anyString(), anyString())).thenReturn(new PrecheckModel.Reply(Samples.VALID,"async-actual-model"));
    }

    @Test void hostEventReturnsImmediatelyAndSameVersionIsIdempotent() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(model.call(anyString(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("model release timed out");
            return new PrecheckModel.Reply(Samples.VALID,"async-actual-model");
        });
        String eventId="EV-"+UUID.randomUUID();
        long ticketVersion=version();
        String body=json.writeValueAsString(new TicketQueueEvent(eventId,"DEMO-001",ticketVersion));
        var first=mvc.perform(post("/api/internal/ticket-events/complaint-queue")
                        .with(httpBasic("host","host-test-password")).contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.created").value(true)).andReturn();
        String runId=json.readTree(first.getResponse().getContentAsString()).path("runId").asText();
        assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
        mvc.perform(post("/api/internal/ticket-events/complaint-queue")
                        .with(httpBasic("host","host-test-password")).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.created").value(false));
        assertThat(runs.findEventVersion("mock-host","DEMO-001",ticketVersion).id()).isEqualTo(runId);
        release.countDown();
        await(() -> "COMPLETED".equals(runs.find(runId).taskStatus()));
        verify(model,times(1)).call(anyString(),anyString());
    }

    @Test void retriesTransientFailureThenFallsBackToManualReview() {
        when(model.call(anyString(), anyString())).thenThrow(new PrecheckFailure(PrecheckFailure.Code.MODEL_TIMEOUT));
        String eventId="EV-"+UUID.randomUUID();
        var receipt=async.accept(new TicketQueueEvent(eventId,"DEMO-001",version()),"retry-trace");
        await(() -> {
            var row=runs.find(receipt.runId());
            return row != null && "RETRY_WAIT".equals(row.taskStatus());
        });
        async.dispatch(receipt.runId());
        await(() -> "FAILED".equals(runs.find(receipt.runId()).taskStatus()));
        var row=runs.find(receipt.runId());
        assertThat(row.attemptCount()).isEqualTo(2);
        assertThat(row.status()).isEqualTo("PENDING_REVIEW");
        assertThat(row.failureCode()).isEqualTo("MODEL_TIMEOUT");
    }

    @Test void newerTicketVersionMakesOldResultStaleAndEventIdsCannotBeReused() throws Exception {
        String firstEvent="EV-"+UUID.randomUUID();
        long oldVersion=version();
        when(model.call(anyString(),anyString())).thenReturn(new PrecheckModel.Reply(
                Samples.forTicket("002").replace(",\"AUTH-002\"",""),"async-actual-model"));
        var old=async.accept(new TicketQueueEvent(firstEvent,"DEMO-002",oldVersion),"old-trace");
        var current=async.accept(new TicketQueueEvent("EV-"+UUID.randomUUID(),"DEMO-002",oldVersion+1),"new-trace");
        await(() -> "COMPLETED".equals(runs.find(current.runId()).taskStatus()));
        assertThat(runs.find(old.runId()).currentResult()).isFalse();
        assertThat(runs.find(current.runId()).currentResult()).isTrue();
        String conflict=json.writeValueAsString(new TicketQueueEvent(firstEvent,"DEMO-002",oldVersion+2));
        mvc.perform(post("/api/internal/ticket-events/complaint-queue")
                        .with(httpBasic("host","host-test-password")).contentType("application/json").content(conflict))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_CONFLICT"));
    }

    @Test void interruptedAttemptIsRecoveredAndCannotOverwriteItsReplacement() throws Exception {
        var calls=new AtomicInteger();
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        when(model.call(anyString(),anyString())).thenAnswer(invocation -> {
            if(calls.incrementAndGet()==1) {
                entered.countDown();
                if(!release.await(10,TimeUnit.SECONDS)) throw new AssertionError("model release timed out");
            }
            return new PrecheckModel.Reply(Samples.VALID,"async-actual-model");
        });
        var receipt=async.accept(new TicketQueueEvent("EV-"+UUID.randomUUID(),"DEMO-001",version()),"recovery-trace");
        assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
        jdbc.update("UPDATE ai_precheck_run SET updated_at=TIMESTAMPADD(MINUTE,-10,UTC_TIMESTAMP(6)) WHERE id=?",receipt.runId());
        assertThat(store.recoverTask(receipt.runId())).isTrue();
        assertThat(runs.find(receipt.runId()).taskStatus()).isEqualTo("RETRY_WAIT");
        async.dispatch(receipt.runId());
        release.countDown();
        await(() -> "COMPLETED".equals(runs.find(receipt.runId()).taskStatus()));
        assertThat(runs.find(receipt.runId()).attemptCount()).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test @WithMockUser(username="operator",roles="OPERATOR")
    void simulatedHostTransitionDoesNotWaitForModel() throws Exception {
        var release=new CountDownLatch(1);
        when(model.call(anyString(),anyString())).thenAnswer(invocation -> {
            release.await(10,TimeUnit.SECONDS);
            return new PrecheckModel.Reply(Samples.VALID,"async-actual-model");
        });
        String body="{\"eventId\":\"EV-"+UUID.randomUUID()+"\",\"ticketVersion\":"+version()+"}";
        try {
            mvc.perform(post("/api/mock-host/tickets/DEMO-001/complaint-queue").with(csrf())
                            .contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.transitioned").value(true))
                    .andExpect(jsonPath("$.precheckAccepted").value(true));
        } finally { release.countDown(); }
    }

    @Test void internalEndpointRequiresHostIdentityAndValidContract() throws Exception {
        String body="{\"eventId\":\"EV-"+UUID.randomUUID()+"\",\"ticketId\":\"DEMO-001\",\"ticketVersion\":"+version()+"}";
        mvc.perform(post("/api/internal/ticket-events/complaint-queue").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/internal/ticket-events/complaint-queue").with(user("operator").roles("OPERATOR"))
                        .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/internal/ticket-events/complaint-queue").with(httpBasic("host","host-test-password"))
                        .contentType("application/json").content("{\"eventId\":\"bad id\"}"))
                .andExpect(status().isBadRequest());
    }

    private static void await(BooleanSupplier condition) {
        long deadline=System.nanoTime()+Duration.ofSeconds(8).toNanos();
        while(System.nanoTime()<deadline) {
            if(condition.getAsBoolean()) return;
            try { Thread.sleep(25); }
            catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        }
        fail("async condition timed out");
    }
}
