package se.comerit.seb.infrastructure.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application (on H2, without Flyway) to check that the audit signer is wired in
 * and that /actuator/health is reachable and reveals no details.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:audit-health-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "audit.signing-required=false"
})
@AutoConfigureMockMvc
class AuditSignerWiringTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditSignerService signer;

    @Test
    void signerBeanIsWiredAndDoesNotBreakStartupWithoutNativeLibrary() {
        // No jna.library.path in this test, so signing is simply disabled
        assertFalse(signer.isEnabled());
    }

    @Test
    void healthEndpointIsOpenAndHidesDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("propertySources"))));
    }
}
