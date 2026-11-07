package dev.zzdiogo.jobtracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthApiTest extends ApiTestSupport {

    @Test
    void registeredUserCanLogIn() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(json(post("/api/auth/login"), credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(json(post("/api/auth/login"), credentials(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/auth/register"), credentials(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameResponse() throws Exception {
        String email = uniqueEmail();
        register(email);
        String wrongPassword = mvc.perform(json(post("/api/auth/login"), credentials(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(json(post("/api/auth/login"), credentials(uniqueEmail(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", ""})
    void weakPasswordIsRejected(String password) throws Exception {
        mvc.perform(json(post("/api/auth/register"), credentials(uniqueEmail(), password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("password")));
    }

    @Test
    void passwordOver72BytesIsRejectedEvenWhenUnder72Characters() throws Exception {
        mvc.perform(json(post("/api/auth/register"), credentials(uniqueEmail(), "é".repeat(40))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        mvc.perform(json(post("/api/auth/register"), credentials("not-an-email", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiRequiresAToken() throws Exception {
        mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = register();
        mvc.perform(auth(get("/api/applications"), token + "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void webPageIsPublicAndHasASecurityPolicy() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
    }

    @Test
    void anythingElseIsDenied() throws Exception {
        mvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
    }
}
