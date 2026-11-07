package dev.zzdiogo.jobtracker;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JobApplicationApiTest extends ApiTestSupport {

    @Test
    void createsWithDefaultsAndTrimmedText() throws Exception {
        String token = register();
        mvc.perform(json(auth(post("/api/applications"), token), """
                        {"company": "  Acme  ", "role": " Dev "}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.role").value("Dev"))
                .andExpect(jsonPath("$.status").value("WISHLIST"))
                .andExpect(jsonPath("$.appliedOn").value(nullValue()));
    }

    @Test
    void applyingFillsInTheAppliedDate() throws Exception {
        String token = register();
        long id = createApplication(token, """
                {"company": "Acme", "role": "Dev"}""");
        mvc.perform(json(auth(put("/api/applications/" + id), token), """
                        {"company": "Acme", "role": "Dev", "status": "APPLIED"}"""))
                .andExpect(jsonPath("$.appliedOn").value(LocalDate.now(ZoneOffset.UTC).toString()));
    }

    @Test
    void aDateTheUserEnteredIsKept() throws Exception {
        String token = register();
        long id = createApplication(token, """
                {"company": "Acme", "role": "Dev", "status": "APPLIED", "appliedOn": "2026-01-15"}""");
        mvc.perform(auth(get("/api/applications/" + id), token))
                .andExpect(jsonPath("$.appliedOn").value("2026-01-15"));
    }

    @Test
    void historyRecordsEveryRealStatusChange() throws Exception {
        String token = register();
        long id = createApplication(token);
        String path = "/api/applications/" + id;
        mvc.perform(json(auth(put(path), token), """
                {"company": "Acme", "role": "Dev", "status": "APPLIED"}""")).andExpect(status().isOk());
        mvc.perform(json(auth(put(path), token), """
                {"company": "Acme", "role": "Dev", "status": "INTERVIEW"}""")).andExpect(status().isOk());
        mvc.perform(json(auth(put(path), token), """
                {"company": "Acme", "role": "Dev"}""")).andExpect(status().isOk());

        mvc.perform(auth(get(path + "/history"), token))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].from").value(nullValue()))
                .andExpect(jsonPath("$[0].to").value("APPLIED"))
                .andExpect(jsonPath("$[1].from").value("APPLIED"))
                .andExpect(jsonPath("$[1].to").value("INTERVIEW"));
    }

    @Test
    void listFiltersByStatusAndPages() throws Exception {
        String token = register();
        createApplication(token);
        createApplication(token);
        createApplication(token, """
                {"company": "Other", "role": "Dev", "status": "REJECTED"}""");

        mvc.perform(auth(get("/api/applications").param("status", "REJECTED"), token))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].company").value("Other"));
        mvc.perform(auth(get("/api/applications").param("size", "2"), token))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=101", "page=-1", "status=NOPE"})
    void badListParametersAreRejected(String query) throws Exception {
        String token = register();
        String[] parts = query.split("=");
        mvc.perform(auth(get("/api/applications").param(parts[0], parts[1]), token))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)",
        "JAVASCRIPT:alert(1)",
        "data:text/html,hi",
        "ftp://example.com",
        "not a link"
    })
    void onlyWebLinksAreAccepted(String url) throws Exception {
        String token = register();
        mvc.perform(json(auth(post("/api/applications"), token), """
                        {"company": "Acme", "role": "Dev", "url": "%s"}""".formatted(url)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("url")));
    }

    @Test
    void webLinksAreAccepted() throws Exception {
        String token = register();
        createApplication(token, """
                {"company": "Acme", "role": "Dev", "url": "https://example.com/jobs/1?a=b"}""");
    }

    @Test
    void missingRequiredFieldsAreRejected() throws Exception {
        String token = register();
        mvc.perform(json(auth(post("/api/applications"), token), """
                        {"company": " "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("company")))
                .andExpect(jsonPath("$.detail").value(containsString("role")));
    }

    @Test
    void unknownStatusValueIsABadRequestNotAServerError() throws Exception {
        String token = register();
        mvc.perform(json(auth(post("/api/applications"), token), """
                        {"company": "Acme", "role": "Dev", "status": "NOPE"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingRemovesTheApplicationAndItsChildren() throws Exception {
        String token = register();
        long id = createApplication(token);
        String path = "/api/applications/" + id;
        mvc.perform(json(auth(post(path + "/interviews"), token), """
                {"stage": "TECHNICAL"}""")).andExpect(status().isCreated());

        mvc.perform(auth(delete(path), token)).andExpect(status().isNoContent());

        mvc.perform(auth(get(path), token)).andExpect(status().isNotFound());
        mvc.perform(auth(get(path + "/interviews"), token)).andExpect(status().isNotFound());
    }

    @Test
    void usersCannotSeeOrChangeEachOthersData() throws Exception {
        String owner = register();
        String other = register();
        long id = createApplication(owner);
        String path = "/api/applications/" + id;
        String body = """
                {"company": "Hacked", "role": "Hacked"}""";

        mvc.perform(auth(get(path), other)).andExpect(status().isNotFound());
        mvc.perform(json(auth(put(path), other), body)).andExpect(status().isNotFound());
        mvc.perform(auth(delete(path), other)).andExpect(status().isNotFound());
        mvc.perform(auth(get(path + "/history"), other)).andExpect(status().isNotFound());
        mvc.perform(auth(get(path + "/interviews"), other)).andExpect(status().isNotFound());
        mvc.perform(json(auth(post(path + "/interviews"), other), """
                {"stage": "HR"}""")).andExpect(status().isNotFound());
        mvc.perform(auth(get(path + "/reminders"), other)).andExpect(status().isNotFound());
        mvc.perform(json(auth(post(path + "/reminders"), other), """
                {"remindAt": "2099-01-01T00:00:00Z", "message": "x"}""")).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/applications"), other)).andExpect(jsonPath("$.totalItems").value(0));

        mvc.perform(auth(get(path), owner)).andExpect(jsonPath("$.company").value("Acme"));
    }
}
