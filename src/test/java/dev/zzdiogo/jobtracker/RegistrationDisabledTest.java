package dev.zzdiogo.jobtracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.registration-enabled=false")
class RegistrationDisabledTest extends ApiTestSupport {

    @Test
    void registrationCanBeTurnedOff() throws Exception {
        mvc.perform(json(post("/api/auth/register"), credentials(uniqueEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
    }
}
