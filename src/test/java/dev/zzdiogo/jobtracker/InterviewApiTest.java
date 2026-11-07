package dev.zzdiogo.jobtracker;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

class InterviewApiTest extends ApiTestSupport {

    private long addInterview(String token, long applicationId) throws Exception {
        String body = mvc.perform(json(auth(post("/api/applications/" + applicationId + "/interviews"), token), """
                        {"stage": "TECHNICAL", "scheduledAt": "2027-03-01T10:00:00Z", "notes": "Live coding"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void addsUpdatesListsAndDeletes() throws Exception {
        String token = register();
        long applicationId = createApplication(token);
        long id = addInterview(token, applicationId);
        String base = "/api/applications/" + applicationId + "/interviews";

        mvc.perform(json(auth(put(base + "/" + id), token), """
                        {"stage": "TECHNICAL", "outcome": "PASSED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PASSED"));
        mvc.perform(auth(get(base), token))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].stage").value("TECHNICAL"));
        mvc.perform(auth(delete(base + "/" + id), token)).andExpect(status().isNoContent());
        mvc.perform(auth(get(base), token)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void stageIsRequired() throws Exception {
        String token = register();
        long applicationId = createApplication(token);
        mvc.perform(json(auth(post("/api/applications/" + applicationId + "/interviews"), token), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anInterviewCannotBeReachedThroughAnotherApplication() throws Exception {
        String token = register();
        long first = createApplication(token);
        long second = createApplication(token);
        long id = addInterview(token, first);
        mvc.perform(auth(delete("/api/applications/" + second + "/interviews/" + id), token))
                .andExpect(status().isNotFound());
    }
}
