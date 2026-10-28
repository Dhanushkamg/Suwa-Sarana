package com.suwasarana.api.contact;

import com.suwasarana.api.ratelimit.RateLimitService;
import com.suwasarana.api.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ContactIntegrationTest {

    private static final String VALID = """
            {"name":"Nimali Perera","email":"nimali@example.com","subject":"Camp question",
             "message":"Is there a donation camp in Kandy next week?"}""";

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private WebApplicationContext context;
    @Autowired private ContactRepository repository;
    @Autowired private RateLimitService limits;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        repository.deleteAll();
        limits.store().clear();
    }

    private static RequestPostProcessor as(String role) {
        return user(new UserDetailsImpl(1L, "admin@example.com", "x", role));
    }

    @Test
    @DisplayName("A valid message is stored with status NEW")
    void validMessageIsStored() throws Exception {
        mvc.perform(post("/api/contact").contentType(JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertEquals(1, repository.count());
        ContactMessage m = repository.findAll().get(0);
        assertEquals(ContactStatus.NEW, m.getStatus());
        assertNotNull(m.getCreatedAt());
        assertNull(m.getHandledAt());
    }

    @Test
    @DisplayName("Filled honeypot: same success answer, nothing stored")
    void honeypotIsDroppedSilently() throws Exception {
        String bot = VALID.replace("}", ",\"website\":\"http://spam.example\"}");
        mvc.perform(post("/api/contact").contentType(JSON).content(bot))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertEquals(0, repository.count());
    }

    @Test
    @DisplayName("Too short or too long fields are rejected with 400")
    void sizeLimits() throws Exception {
        String tooShort = VALID.replace("Is there a donation camp in Kandy next week?", "hi");
        mvc.perform(post("/api/contact").contentType(JSON).content(tooShort)).andExpect(status().isBadRequest());
        String tooLong = VALID.replace("Camp question", "x".repeat(201));
        mvc.perform(post("/api/contact").contentType(JSON).content(tooLong)).andExpect(status().isBadRequest());
        String hugeMessage = VALID.replace("Is there a donation camp in Kandy next week?", "y".repeat(2001));
        mvc.perform(post("/api/contact").contentType(JSON).content(hugeMessage)).andExpect(status().isBadRequest());
        assertEquals(0, repository.count());
    }

    @Test
    @DisplayName("Control characters (NUL) are stripped before storing")
    void controlCharactersAreStripped() throws Exception {
        String withNul = VALID.replace("Camp question", "Camp\\u0000 question");
        mvc.perform(post("/api/contact").contentType(JSON).content(withNul)).andExpect(status().isOk());
        assertEquals("Camp question", repository.findAll().get(0).getSubject());
    }

    @Test
    @DisplayName("The public form is limited to 5 messages per IP per hour")
    void publicFormIsRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/contact").contentType(JSON).content(VALID)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/contact").contentType(JSON).content(VALID))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate.limited"));
    }

    @Test
    @DisplayName("Inbox: anonymous 401, non-admin 403, admin 200")
    void inboxAccessControl() throws Exception {
        mvc.perform(get("/api/admin/contact-messages")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/contact-messages").with(as("DONOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/contact-messages").with(as("HOSPITAL_REQUESTER"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/contact-messages/1").with(as("REQUESTER"))
                .contentType(JSON).content("{\"status\":\"HANDLED\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/contact-messages").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Inbox is paged, filterable and newest first; marking handled records who and when")
    void inboxPagingAndHandling() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/contact").contentType(JSON)
                    .content(VALID.replace("Camp question", "Question number " + i))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/admin/contact-messages?size=2&page=0").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].subject").value("Question number 3"));

        Long firstId = repository.findAll().get(0).getId();
        mvc.perform(patch("/api/admin/contact-messages/" + firstId).with(as("ADMIN"))
                        .contentType(JSON).content("{\"status\":\"HANDLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HANDLED"))
                .andExpect(jsonPath("$.data.handledBy").value("admin@example.com"));

        mvc.perform(get("/api/admin/contact-messages?status=NEW").with(as("ADMIN")))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/admin/contact-messages?status=HANDLED").with(as("ADMIN")))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // reopening clears the handling info
        mvc.perform(patch("/api/admin/contact-messages/" + firstId).with(as("ADMIN"))
                        .contentType(JSON).content("{\"status\":\"NEW\"}"))
                .andExpect(jsonPath("$.data.handledAt").doesNotExist());
    }

    @Test
    @DisplayName("Unknown id is 404, an invalid status is 400, page size is capped")
    void inboxEdgeCases() throws Exception {
        mvc.perform(patch("/api/admin/contact-messages/99999").with(as("ADMIN"))
                .contentType(JSON).content("{\"status\":\"HANDLED\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/contact-messages/1").with(as("ADMIN"))
                .contentType(JSON).content("{\"status\":\"BOGUS\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/contact-messages?size=100000").with(as("ADMIN")))
                .andExpect(jsonPath("$.data.size").value(100));
    }
}
