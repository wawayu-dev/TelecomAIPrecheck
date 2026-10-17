package cn.telecom.precheck.precheck;

import cn.telecom.precheck.context.TicketContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS",matches="true")
class PrecheckIntegrationTest {
    @Autowired PrecheckService service;
    @Autowired PrecheckStore store;
    @Autowired PrecheckMapper mapper;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TicketContextProvider provider;
    @MockitoBean PrecheckModel model;
    @BeforeEach void configureModel() {
        when(model.modelId()).thenReturn("synthetic-test-model");
        when(model.call(anyString(),anyString())).thenReturn(new PrecheckModel.Reply(Samples.VALID,"reported-model"));
    }
    @Test void savesSuccessHistoryAndIndependentEvidenceSnapshot() throws Exception {
        var response=mvc.perform(post("/api/ai-prechecks/tickets/DEMO-001"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.run.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.run.callStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.result.evidenceIds[0]").value("BILL-001"))
                .andExpect(jsonPath("$.run.resultJson").doesNotExist()).andReturn().getResponse();
        var data=json.readTree(response.getContentAsString());
        String id=data.path("run").path("id").asText();
        assertThat(data.path("run").path("traceId").asText()).isEqualTo(response.getHeader("X-Trace-Id"));
        var saved=service.get(id);
        assertThat(saved.run().actualModel()).isEqualTo("reported-model");
        assertThat(saved.run().promptVersion()).isEqualTo(PromptCatalog.VERSION);
        assertThat(saved.run().durationMs()).isNotNegative();
        assertThat(saved.run().completedAt()).isNotNull();
        assertThat(saved.evidenceSnapshot().toString()).contains("BILL-001");
        assertThat(service.history("DEMO-001",100,0)).extracting(v -> v.run().id()).contains(id);
        mvc.perform(get("/api/ai-prechecks/{id}",id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceSnapshot.authorizations.items[0].evidenceId").value("AUTH-001"));
        verify(model).call(anyString(),argThat(payload -> !payload.contains("customerRef")));
    }
    @Test void missingAuthorizationIsSavedWithSafeAdvice() {
        when(model.call(anyString(),anyString())).thenReturn(new PrecheckModel.Reply(
                Samples.forTicket("002").replace(",\"AUTH-002\"",""),"reported-model"));
        var view=service.start("DEMO-002","test-trace");
        assertThat(view.run().callStatus()).isEqualTo("SUCCESS");
        assertThat(view.result().toString()).contains("VERIFY_AUTHORIZATION");
        assertThat(view.findings().toString()).contains("AUTHORIZATION_MISSING");
    }
    @Test void amountMismatchIsPersistedWithEvidenceReferences() {
        when(model.call(anyString(),anyString())).thenReturn(new PrecheckModel.Reply(Samples.forTicket("003"),"reported-model"));
        var view=service.start("DEMO-003","test-trace");
        assertThat(view.findings().toString()).contains("AMOUNT_MISMATCH","BILL-003","ORDER-003");
    }
    @Test void nonexistentEvidenceIsRejectedButRunAndTicketRemain() {
        when(model.call(anyString(),anyString())).thenReturn(new PrecheckModel.Reply(Samples.VALID.replace("BILL-001","FAKE"),"reported-model"));
        var view=service.start("DEMO-001","test-trace");
        assertThat(view.run().callStatus()).isEqualTo("FAILED");
        assertThat(view.run().failureCode()).isEqualTo("INVALID_EVIDENCE_REFERENCE");
        assertThat(view.run().status()).isEqualTo("PENDING_REVIEW");
        assertThat(view.result()).isNull();
        assertThat(view.evidenceSnapshot()).isNotNull();
        assertThat(provider.getContext("DEMO-001").ticketId()).isEqualTo("DEMO-001");
    }
    @ParameterizedTest
    @EnumSource(value=PrecheckFailure.Code.class,names={"MODEL_TIMEOUT","MODEL_ERROR","MODEL_NOT_CONFIGURED"})
    void failuresRemainAvailableForManualHandling(PrecheckFailure.Code code) {
        when(model.call(anyString(),anyString())).thenThrow(new PrecheckFailure(code));
        var view=service.start("DEMO-001","test-trace");
        assertThat(view.run().failureCode()).isEqualTo(code.name());
        assertThat(view.run().status()).isEqualTo("PENDING_REVIEW");
        assertThat(view.run().callStatus()).isEqualTo("FAILED");
        assertThat(view.result()).isNull();
        assertThat(view.evidenceSnapshot()).isNotNull();
    }
    @Test void malformedStructuredOutputIsNotStored() {
        when(model.call(anyString(),anyString())).thenReturn(new PrecheckModel.Reply("secret-invalid-output","reported-model"));
        var view=service.start("DEMO-001","test-trace");
        assertThat(view.run().failureCode()).isEqualTo("INVALID_OUTPUT");
        assertThat(view.run().resultJson()).isNull();
    }
    @Test void snapshotWriteFailureRollsBackRunCreation() {
        String id=UUID.randomUUID().toString();
        assertThatThrownBy(() -> store.begin(id,"DEMO-001","test","trace","invalid-json"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(mapper.find(id)).isNull();
        assertThat(mapper.snapshotJson(id)).isNull();
    }
    @Test void recoveryClosesInterruptedRunsWithoutAllowingLateOverwrite() {
        String id=UUID.randomUUID().toString();
        store.begin(id,"DEMO-001","test","trace","{}");
        jdbc.update("UPDATE ai_precheck_run SET created_at=TIMESTAMPADD(MINUTE,-10,UTC_TIMESTAMP(6)) WHERE id=?",id);
        mapper.recoverInterrupted();
        store.finish(id,"SUCCESS","late-model",10,null,"{}","[]");
        var view=service.get(id);
        assertThat(view.run().failureCode()).isEqualTo("INTERRUPTED");
        assertThat(view.run().callStatus()).isEqualTo("FAILED");
        assertThat(view.run().version()).isEqualTo(1);
    }
    @Test void invalidRequestsAndUnknownRunHaveControlledErrors() throws Exception {
        mvc.perform(get("/api/ai-prechecks/tickets/DEMO-001?limit=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ai-prechecks/tickets/DEMO-001?limit=text")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ai-prechecks/{id}",UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post("/api/ai-prechecks/tickets/UNKNOWN")).andExpect(status().isNotFound());
        verify(model,never()).call(anyString(),anyString());
    }
}
