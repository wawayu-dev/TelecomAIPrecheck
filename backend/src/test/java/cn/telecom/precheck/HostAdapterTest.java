package cn.telecom.precheck;

import cn.telecom.precheck.adapter.MockTicketContextProvider;
import cn.telecom.precheck.context.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {PrecheckApplication.class, HostAdapterTest.HostConfiguration.class},
        properties = "precheck.context-provider=host")
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class HostAdapterTest {
    @Autowired MockMvc mvc;
    @Autowired java.util.Map<String, TicketContextProvider> providers;
    @TestConfiguration
    static class HostConfiguration {
        @Bean TicketContextProvider hostProvider() {
            return id -> {
                if (id.equals("DOWN")) throw new ContextUnavailableException();
                if (id.equals("ERROR")) throw new IllegalStateException("secret-password-and-customer-data");
                return new MockTicketContextProvider().getContext("DEMO-001");
            };
        }
    }
    @Test void hostBeanReplacesMockWithoutDatabaseTables() throws Exception {
        assertThat(providers).containsOnlyKeys("hostProvider");
        mvc.perform(get("/api/tickets/HOST-001/context")).andExpect(status().isOk());
    }
    @Test void unavailableHostReturns503() throws Exception {
        mvc.perform(get("/api/tickets/DOWN/context")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CONTEXT_UNAVAILABLE"));
    }
    @Test void unexpectedFailureDoesNotExposeHostException() throws Exception {
        var response = mvc.perform(get("/api/tickets/ERROR/context"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("secret-password", "customer-data", "IllegalStateException");
    }
}
