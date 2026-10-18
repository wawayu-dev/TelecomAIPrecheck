package cn.telecom.precheck.review;

import cn.telecom.precheck.precheck.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"DEMO_REVIEWER_PASSWORD=reviewer-test-password","precheck.demo-login.enabled=true"})
@AutoConfigureMockMvc @ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS",matches="true")
@WithMockUser(username="reviewer",roles={"OPERATOR","REVIEWER","AUDITOR"})
class ReviewIntegrationTest {
    @Autowired PrecheckService prechecks;
    @Autowired PrecheckStore store;
    @Autowired PrecheckMapper runs;
    @Autowired ReviewService service;
    @Autowired ReviewMapper reviews;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PrecheckModel model;
    @MockitoSpyBean AuditMapper audits;
    @BeforeEach void modelFails() {
        when(model.modelId()).thenReturn("test-model");
        when(model.call(anyString(),anyString())).thenThrow(new PrecheckFailure(PrecheckFailure.Code.MODEL_TIMEOUT));
    }
    String pending() { return prechecks.start("DEMO-001","start-trace").run().id(); }
    ReviewRequest request(int version,ReviewRequest.Decision decision) {
        return new ReviewRequest(version,decision,"人工核对：请补充授权材料",PrecheckResult.Recommendation.VERIFY_AUTHORIZATION,"尚缺授权依据，转人工核实");
    }
    @ParameterizedTest @EnumSource(ReviewRequest.Decision.class)
    void failedAiCanBeReviewedWithCompleteTrace(ReviewRequest.Decision decision) {
        String id=pending();
        var original=prechecks.get(id);
        var review=service.submit(id,request(1,decision),"review-trace");
        assertThat(review.actorId()).isEqualTo("reviewer");
        assertThat(review.decision()).isEqualTo(decision.name());
        assertThat(review.version()).isEqualTo(2);
        assertThat(review.reviewedAt()).isBetween(java.time.Instant.now().minusSeconds(30),java.time.Instant.now().plusSeconds(2));
        assertThat(original.run().createdAt()).isBetween(java.time.Instant.now().minusSeconds(30),java.time.Instant.now().plusSeconds(2));
        assertThat(runs.find(id).status()).isEqualTo("REVIEWED");
        assertThat(prechecks.get(id).evidenceSnapshot()).isEqualTo(original.evidenceSnapshot());
        assertThat(runs.find(id).failureCode()).isEqualTo("MODEL_TIMEOUT");
        var events=service.audit(id,0,50);
        assertThat(events).extracting(AuditEvent::action).containsExactly("PRECHECK_STARTED","PRECHECK_FAILED","REVIEW_"+decision);
        assertThat(events).extracting(AuditEvent::runVersion).containsExactly(0,1,2);
        assertThat(events).allSatisfy(e -> { assertThat(e.snapshotRunId()).isEqualTo(id); assertThat(e.promptVersion()).isEqualTo(PromptCatalog.VERSION); });
        assertThat(events.get(2).traceId()).isEqualTo("review-trace");
        assertThat(events.get(2).occurredAt()).isBetween(java.time.Instant.now().minusSeconds(30),java.time.Instant.now().plusSeconds(2));
        assertThat(events.get(2).fromStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(events.get(2).toStatus()).isEqualTo("REVIEWED");
        assertThat(service.audit(id,events.get(1).id(),1)).hasSize(1);
        var timeline=service.timeline(id,0,50);
        assertThat(timeline).hasSize(3);
        assertThat(timeline.get(2).reviewChange().aiResult()).isNull();
        assertThat(timeline.get(2).reviewChange().humanResult().summary()).contains("人工核对");
    }
    @Test void successfulAiIsImmutableAfterHumanCorrection() {
        String id=UUID.randomUUID().toString();
        store.begin(id,"DEMO-001","test-model","trace","{\"synthetic\":true}");
        String original="{\"summary\":\"原始AI摘要\"}";
        store.finish(id,"SUCCESS","actual-model",12,null,original,"[]");
        service.submit(id,request(1,ReviewRequest.Decision.CONFIRMED),"review-trace");
        assertThat(prechecks.get(id).result().path("summary").asText()).isEqualTo("原始AI摘要");
        assertThat(service.get(id).summary()).contains("人工核对");
        assertThat(service.audit(id,0,50).get(2).model()).isEqualTo("actual-model");
        var change=service.timeline(id,0,50).get(2).reviewChange();
        assertThat(change.aiResult().path("summary").asText()).isEqualTo("原始AI摘要");
        assertThat(change.humanResult().summary()).contains("人工核对");
    }
    @Test void analyzingStaleAndRepeatedReviewsAreRejected() {
        String id=UUID.randomUUID().toString();
        store.begin(id,"DEMO-001","test-model","trace","{}");
        assertThatThrownBy(() -> service.submit(id,request(0,ReviewRequest.Decision.CONFIRMED),"trace")).isInstanceOf(ReviewConflictException.class);
        store.finish(id,"FAILED",null,0,"MODEL_ERROR",null,"[]");
        assertThatThrownBy(() -> service.submit(id,request(0,ReviewRequest.Decision.CONFIRMED),"trace")).isInstanceOf(ReviewConflictException.class);
        service.submit(id,request(1,ReviewRequest.Decision.REJECTED),"trace");
        assertThatThrownBy(() -> service.submit(id,request(2,ReviewRequest.Decision.CONFIRMED),"trace")).isInstanceOf(ReviewConflictException.class);
        assertThat(audits.list(id,0,100)).hasSize(3);
    }
    @Test void concurrentReviewHasExactlyOneWinner() throws Exception {
        String id=pending();
        var pool=Executors.newFixedThreadPool(2);
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try {
            var tasks=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++) {
                final String actor="reviewer"+i;
                tasks.add(pool.submit(() -> {
                    var context=SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new UsernamePasswordAuthenticationToken(actor,"N/A",List.of(new SimpleGrantedAuthority("ROLE_REVIEWER"))));
                    SecurityContextHolder.setContext(context);
                    try {
                        ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Barrier timed out");
                        service.submit(id,request(1,ReviewRequest.Decision.CONFIRMED),actor+"-trace"); return true;
                    } catch(ReviewConflictException expected) { return false; }
                    finally { SecurityContextHolder.clearContext(); }
                }));
            }
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            int wins=0; for(var result:tasks) if(result.get(15,TimeUnit.SECONDS)) wins++;
            assertThat(wins).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_review WHERE run_id=?",Integer.class,id)).isEqualTo(1);
            assertThat(runs.find(id).version()).isEqualTo(2);
            assertThat(audits.list(id,0,100)).hasSize(3);
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    @Test void auditFailureRollsBackStateAndReview() {
        String id=pending();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated audit failure"))
            .when(audits).append(eq(id),anyString(),eq("REVIEW_CONFIRMED"),anyString(),anyString(),anyString());
        assertThatThrownBy(() -> service.submit(id,request(1,ReviewRequest.Decision.CONFIRMED),"trace"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(runs.find(id).status()).isEqualTo("PENDING_REVIEW");
        assertThat(runs.find(id).version()).isEqualTo(1);
        assertThat(reviews.find(id)).isNull();
        assertThat(audits.list(id,0,100)).hasSize(2);
    }
    @Test void precheckCreationAndCompletionAlsoRollBackWhenAuditFails() {
        String id=UUID.randomUUID().toString();
        doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated audit failure"))
            .when(audits).append(eq(id),anyString(),eq("PRECHECK_STARTED"),isNull(),anyString(),anyString());
        assertThatThrownBy(() -> store.begin(id,"DEMO-001","test-model","trace","{}"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(runs.find(id)).isNull(); assertThat(runs.snapshotJson(id)).isNull();
        String second=UUID.randomUUID().toString();
        store.begin(second,"DEMO-001","test-model","trace","{}");
        doThrow(new org.springframework.dao.DataIntegrityViolationException("simulated audit failure"))
            .when(audits).append(eq(second),anyString(),eq("PRECHECK_SUCCESS"),anyString(),anyString(),anyString());
        assertThatThrownBy(() -> store.finish(second,"SUCCESS","actual",1,null,"{}","[]"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(runs.find(second).status()).isEqualTo("ANALYZING");
        assertThat(runs.find(second).version()).isZero();
        assertThat(audits.list(second,0,100)).hasSize(1);
    }
    @Test @WithMockUser(username="operator",roles="OPERATOR")
    void methodSecurityAlsoProtectsNonHttpCalls() {
        String id=pending();
        assertThatThrownBy(() -> prechecks.get(id))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> prechecks.history("DEMO-001",20,0))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> prechecks.current("DEMO-001"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.get(id))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.submit(id,request(1,ReviewRequest.Decision.CONFIRMED),"trace"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.audit(id,0,50))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(reviews.find(id)).isNull();
    }
    @Test void recoveryIsAuditedOnceAndLateModelCannotOverwrite() {
        String id=UUID.randomUUID().toString(); store.begin(id,"DEMO-001","test-model","trace","{}");
        jdbc.update("UPDATE ai_precheck_run SET created_at=TIMESTAMPADD(MINUTE,-10,UTC_TIMESTAMP(6)) WHERE id=?",id);
        store.recover(id); store.recover(id); store.finish(id,"SUCCESS","late",3,null,"{}","[]");
        assertThat(audits.list(id,0,100)).extracting(AuditEvent::action).containsExactly("PRECHECK_STARTED","PRECHECK_INTERRUPTED");
        assertThat(runs.find(id).failureCode()).isEqualTo("INTERRUPTED");
    }
    @Test void opinionsAreMaskedAndNotCopiedIntoAudit() throws Exception {
        String id=pending();
        var body=new ReviewRequest(1,ReviewRequest.Decision.REJECTED,"联系13812345678 / a@example.com",PrecheckResult.Recommendation.MANUAL_REVIEW,"证件110101199001011234");
        var saved=service.submit(id,body,"trace");
        assertThat(saved.summary()).doesNotContain("13812345678","a@example.com").contains("已脱敏");
        assertThat(saved.reason()).doesNotContain("110101199001011234");
        assertThat(json.writeValueAsString(audits.list(id,0,100))).doesNotContain("联系","证件","13812345678");
    }
    @Test @WithAnonymousUser void authenticationIsRequiredButHealthIsPublic() throws Exception {
        mvc.perform(get("/api/ai-prechecks/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andExpect(jsonPath("$.traceId").isNotEmpty());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
    @Test void permissionsCsrfAndForgedIdentityAreRejected() throws Exception {
        String id=pending(); String body=json.writeValueAsString(request(1,ReviewRequest.Decision.CONFIRMED));
        mvc.perform(post("/api/ai-prechecks/{id}/review",id).with(user("operator").roles("OPERATOR")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai-prechecks/{id}/review",id).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai-prechecks/{id}/review",id).with(csrf()).contentType("application/json").content(body.substring(0,body.length()-1)+",\"actorId\":\"admin\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai-prechecks/tickets/DEMO-001").with(user("reviewer").roles("REVIEWER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai-prechecks/{id}",id).with(user("operator").roles("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai-prechecks/tickets/DEMO-001").with(user("operator").roles("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai-prechecks/tickets/DEMO-001/current").with(user("operator").roles("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai-prechecks/{id}/review",id).with(user("operator").roles("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai-prechecks/{id}/retry",id).with(user("operator").roles("OPERATOR")).with(csrf())
                .contentType("application/json").content("{\"expectedVersion\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai-prechecks/{id}/review",id).with(user("auditor").roles("AUDITOR")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai-prechecks/{id}/audit",id).with(user("operator").roles("OPERATOR"))).andExpect(status().isForbidden());
        assertThat(reviews.find(id)).isNull();
    }
    @Test void apiValidatesFieldsAndReturnsConflictAndAudit() throws Exception {
        String id=pending(); String url="/api/ai-prechecks/"+id+"/review";
        for(String invalid:List.of("{}", "{\"expectedVersion\":-1}",json.writeValueAsString(request(1,ReviewRequest.Decision.CONFIRMED)).replace("VERIFY_AUTHORIZATION","REFUND"))) {
            mvc.perform(post(url).with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        String body=json.writeValueAsString(request(1,ReviewRequest.Decision.CONFIRMED));
        mvc.perform(post(url).with(csrf()).header("X-Actor-Id","admin").contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.actorId").value("reviewer"));
        mvc.perform(post(url).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW_CONFLICT"));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("CONFIRMED"));
        mvc.perform(get("/api/ai-prechecks/{id}/audit",id).with(user("auditor").roles("AUDITOR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[2].runVersion").value(2));
        mvc.perform(get("/api/ai-prechecks/{id}/timeline",id).with(user("auditor").roles("AUDITOR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[2].reviewChange.humanResult.actorId").value("reviewer"));
        mvc.perform(get("/api/ai-prechecks/{id}/audit?limit=101",id)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ai-prechecks/{id}/audit",UUID.randomUUID())).andExpect(status().isNotFound());
    }
    @Test @WithAnonymousUser void hostLogoutInvalidatesSessionAndRequiresCsrf() throws Exception {
        var result=mvc.perform(get("/api/session").with(httpBasic("reviewer","reviewer-test-password")))
            .andExpect(status().isOk()).andReturn();
        var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(post("/api/session/logout").session(session).with(httpBasic("reviewer","reviewer-test-password")))
            .andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(post("/api/session/logout").session(session).with(csrf()).with(httpBasic("reviewer","reviewer-test-password")))
            .andExpect(status().isNoContent()).andExpect(cookie().maxAge("JSESSIONID",0));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/session").header("X-Requested-With","XMLHttpRequest").with(httpBasic("reviewer","wrong")))
            .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"));
    }
    @Test @WithAnonymousUser void demoBasicLoginReturnsIdentityAndCsrf() throws Exception {
        mvc.perform(get("/api/session").with(httpBasic("reviewer","reviewer-test-password")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.actorId").value("reviewer"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_REVIEWER"))
            .andExpect(jsonPath("$.csrfToken").isNotEmpty());
        mvc.perform(get("/api/session").with(httpBasic("reviewer","wrong"))).andExpect(status().isUnauthorized());
    }
    @Test @WithAnonymousUser void demoRoleSelectionCreatesAuthenticatedSessionWithoutPassword() throws Exception {
        var result=mvc.perform(post("/api/demo-session").contentType("application/json")
                .content("{\"account\":\"auditor\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.actorId").value("auditor"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_AUDITOR"))
            .andExpect(jsonPath("$.csrfToken").isNotEmpty()).andReturn();
        var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(get("/api/session").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.actorId").value("auditor"));
        mvc.perform(post("/api/demo-session").contentType("application/json")
                .content("{\"account\":\"admin\"}"))
            .andExpect(status().isBadRequest());
    }
}
