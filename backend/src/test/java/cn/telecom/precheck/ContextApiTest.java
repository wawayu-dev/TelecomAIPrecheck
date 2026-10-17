package cn.telecom.precheck;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class ContextApiTest {
    @Autowired MockMvc mvc;

    @Test void healthWorksWithoutDatabaseOrModelCredentials() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }
    @ParameterizedTest
    @CsvSource({"DEMO-001,AVAILABLE,30.00", "DEMO-002,NOT_FOUND,30.00", "DEMO-003,AVAILABLE,20.00"})
    void readsSyntheticEvidence(String id, String authorizationStatus, String price) throws Exception {
        mvc.perform(get("/api/tickets/{id}/context", id)).andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.ticketId").value(id))
                .andExpect(jsonPath("$.synthetic").value(true))
                .andExpect(jsonPath("$.bills.items[0].amount").value("30.00"))
                .andExpect(jsonPath("$.bills.items[0].billingPeriod").value("2026-10"))
                .andExpect(jsonPath("$.subscriptions.items[0].monthlyPrice").value(price))
                .andExpect(jsonPath("$.authorizations.status").value(authorizationStatus));
    }
    @Test void unknownTicketHasSafeErrorAndMatchingTraceId() throws Exception {
        var response = mvc.perform(get("/api/tickets/UNKNOWN/context"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"))
                .andReturn().getResponse();
        org.assertj.core.api.Assertions.assertThat(response.getContentAsString()).contains(response.getHeader("X-Trace-Id"));
    }
    @Test void invalidIdIsRejected() throws Exception {
        mvc.perform(get("/api/tickets/bad.id/context")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void unknownRouteRemains404() throws Exception {
        mvc.perform(get("/api/unknown")).andExpect(status().isNotFound());
    }
    @Test void contextIsReadOnly() throws Exception {
        mvc.perform(post("/api/tickets/DEMO-001/context")).andExpect(status().isMethodNotAllowed());
    }
}
