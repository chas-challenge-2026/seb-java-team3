package se.comerit.seb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import se.comerit.seb.config.JwtSecurityConfig;
import se.comerit.seb.domain.Role;
import se.comerit.seb.security.AuthenticatedUserContext;
import se.comerit.seb.security.JwtService;
import se.comerit.seb.security.RoleAccessDeniedHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Körs genom den riktiga säkerhetskedjan (JwtSecurityConfig), så testet visar också att
// frontend-routes inte kräver någon token. MockMvc utför inte forwarden på riktigt:
// forwardedUrl() visar vart requesten skickades vidare. Att index.html sen faktiskt
// serveras kollas i Docker (#162).
//
// API-testerna skickar med en giltig token. Utan token svarar säkerhetskedjan 401 innan
// fallbacken ens prövas, och då hade testet inte bevisat något om mönstren.
@WebMvcTest(SpaFallbackController.class)
@Import({JwtSecurityConfig.class, JwtService.class, RoleAccessDeniedHandler.class})
class SpaFallbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String validToken() {
        return jwtService.generateToken(new AuthenticatedUserContext(1L, 1L, Role.INITIATOR));
    }

    @Test
    void root_withoutToken_forwardsToIndex() throws Exception {
        // React äger startsidan. Utan token skickar Reacts egen auth-guard vidare till /login.
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void frontendRoute_withoutToken_forwardsToIndex() throws Exception {
        mockMvc.perform(get("/attest"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void nestedFrontendRoute_forwardsToIndex() throws Exception {
        mockMvc.perform(get("/payments/new"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void unknownFrontendRoute_forwardsToIndex() throws Exception {
        // Routes som React inte känner till ska också dit, så att routern kan visa sin Not Found-vy.
        mockMvc.perform(get("/finns-inte"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void unknownApiRoute_returns404_notIndex() throws Exception {
        mockMvc.perform(get("/api/finns-inte")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }

    @Test
    void apiRoot_returns404_notIndex() throws Exception {
        // Ettnivåmönstret måste också utesluta "api", inte bara tvånivåmönstret.
        mockMvc.perform(get("/api")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }

    @Test
    void apiRoute_withoutToken_stillReturns401() throws Exception {
        mockMvc.perform(get("/api/finns-inte"))
                .andExpect(status().isUnauthorized());
    }

    // Filerna finns inte på testets classpath, därav 404. Poängen är att de inte forwardas:
    // i den riktiga jar-filen serveras de som vanliga statiska filer.
    @Test
    void assetFile_isNotForwarded() throws Exception {
        mockMvc.perform(get("/assets/index-abc123.js"))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }

    @Test
    void rootFile_isNotForwarded() throws Exception {
        mockMvc.perform(get("/favicon.svg"))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }

    @Test
    void indexHtml_isNotForwarded() throws Exception {
        // Målet för forwarden måste serveras som fil. Annars skulle forwarden loopa.
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isNotFound())
                .andExpect(forwardedUrl(null));
    }
}
