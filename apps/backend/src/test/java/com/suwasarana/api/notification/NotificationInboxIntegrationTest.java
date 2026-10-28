package com.suwasarana.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.suwasarana.api.notification.dto.NotificationDto;
import com.suwasarana.api.testsupport.LifecycleIntegrationBase;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationInboxIntegrationTest extends LifecycleIntegrationBase {

    @Autowired private NotificationInboxService inbox;
    @Autowired private SseNotificationService sse;
    @Autowired private InboxPledgeNotifier pledgeNotifier;

    private User alice;
    private User bob;

    @BeforeEach
    void fixtures() {
        alice = user("alice", Role.DONOR, VerificationStatus.VERIFIED);
        bob = user("bob", Role.REQUESTER, VerificationStatus.VERIFIED);
    }

    private List<Long> addFor(User u, int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            ids.add(inbox.add(u.getId(), "NEW_MATCH", "notifications.match.title", "notifications.match.body",
                    Map.of("requestId", 100 + i, "bloodType", "O+", "hospital", "Base Hospital", "district", "Kandy", "urgency", "URGENT"), 100L + i).getId());
        }
        return ids;
    }

    @Test
    @DisplayName("Inbox: newest first, keyset paging, unread filter and counts, per user")
    void inboxPaging() throws Exception {
        List<Long> ids = addFor(alice, 5);
        addFor(bob, 1);

        JsonNode page1 = data(mvc.perform(get("/api/notifications").param("limit", "2").with(as(alice))).andExpect(status().isOk()).andReturn());
        assertEquals(2, page1.get("items").size());
        assertEquals(ids.get(4), page1.get("items").get(0).get("id").asLong(), "newest first");
        assertEquals(5, page1.get("unreadCount").asInt());
        long cursor = page1.get("nextCursor").asLong();
        assertEquals(ids.get(3), cursor);

        JsonNode page2 = data(mvc.perform(get("/api/notifications").param("limit", "2").param("cursor", String.valueOf(cursor)).with(as(alice))).andReturn());
        assertEquals(ids.get(2), page2.get("items").get(0).get("id").asLong());
        JsonNode last = data(mvc.perform(get("/api/notifications").param("limit", "2").param("cursor", String.valueOf(page2.get("nextCursor").asLong())).with(as(alice))).andReturn());
        assertEquals(1, last.get("items").size());
        assertTrue(last.get("nextCursor").isNull(), "no further page");

        // the payload is keys + params, with an English rendering as a fallback only
        JsonNode first = page1.get("items").get(0);
        assertEquals("NEW_MATCH", first.get("type").asText());
        assertEquals("notifications.match.title", first.get("titleKey").asText());
        assertEquals("notifications.match.body", first.get("bodyKey").asText());
        assertEquals("O+", first.get("params").get("bloodType").asText());
        assertEquals(105, first.get("referenceId").asInt());
        assertEquals("Urgent blood request", first.get("title").asText());
        assertTrue(first.get("readAt").isNull());

        // mark one read
        mvc.perform(post("/api/notifications/" + ids.get(4) + "/read").with(as(alice))).andExpect(status().isOk());
        assertEquals(4, data(mvc.perform(get("/api/notifications/unread-count").with(as(alice))).andReturn()).get("count").asInt());
        JsonNode unread = data(mvc.perform(get("/api/notifications").param("unread", "true").with(as(alice))).andReturn());
        assertEquals(4, unread.get("items").size());
        assertEquals(ids.get(3), unread.get("items").get(0).get("id").asLong());
        // idempotent
        mvc.perform(post("/api/notifications/" + ids.get(4) + "/read").with(as(alice))).andExpect(status().isOk());

        // someone else's notification is a 404, and it stays unread
        mvc.perform(post("/api/notifications/" + ids.get(0) + "/read").with(as(bob))).andExpect(status().isNotFound());
        assertEquals(1, data(mvc.perform(get("/api/notifications/unread-count").with(as(bob))).andReturn()).get("count").asInt());

        // read all
        assertEquals(4, data(mvc.perform(post("/api/notifications/read-all").with(as(alice))).andExpect(status().isOk()).andReturn()).get("updated").asInt());
        assertEquals(0, data(mvc.perform(get("/api/notifications/unread-count").with(as(alice))).andReturn()).get("count").asInt());
        assertEquals(1, data(mvc.perform(get("/api/notifications/unread-count").with(as(bob))).andReturn()).get("count").asInt(), "other users are untouched");

        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Notifications without keys (older callers) are stored under the generic keys with the text as parameters")
    void legacyMessages() {
        NotificationMessage m = new NotificationMessage("Slot reminder", "See you at 9", "SLOT_REMINDER", 7L);
        NotificationDto stored = inbox.persist(alice.getId(), m);
        assertEquals("notifications.generic.title", stored.getTitleKey());
        assertEquals("Slot reminder", stored.getParams().get("title"));
        assertEquals("Slot reminder", stored.getTitle());
        assertEquals("See you at 9", stored.getBody());
    }

    @Test
    @DisplayName("Several tabs per user: every open stream receives the event; heartbeats do not break anything")
    void multipleStreamsPerUser() {
        SseEmitter tab1 = sse.subscribe(alice.getId());
        SseEmitter tab2 = sse.subscribe(alice.getId());
        SseEmitter bobsTab = sse.subscribe(bob.getId());
        assertEquals(2, sse.connectionCount(alice.getId()));
        assertEquals(1, sse.connectionCount(bob.getId()));
        assertTrue(sse.isConnected(alice.getId()));

        NotificationDto n = inbox.persist(alice.getId(), "NEW_MATCH", "notifications.match.title", "notifications.match.body", Map.of(), null);
        assertEquals(2, sse.push(alice.getId(), n), "both of Alice's tabs get it");
        assertEquals(0, sse.push(user("nobody", Role.DONOR, VerificationStatus.VERIFIED).getId(), n));

        sse.heartbeat();
        assertEquals(2, sse.connectionCount(alice.getId()));

        tab1.complete();
        tab2.complete();
        bobsTab.complete();
        assertNotNull(tab1);
    }

    @Test
    @DisplayName("An old stream is dropped when a user opens more than the allowed number of tabs")
    void streamCapPerUser() {
        for (int i = 0; i < SseNotificationService.MAX_STREAMS_PER_USER + 3; i++) sse.subscribe(alice.getId());
        assertEquals(SseNotificationService.MAX_STREAMS_PER_USER, sse.connectionCount(alice.getId()));
    }

    @Test
    @DisplayName("Reconnecting with Last-Event-ID replays only what was missed, with the notification id as the event id")
    void replayFromLastEventId() throws Exception {
        List<Long> ids = addFor(alice, 4);
        addFor(bob, 2);

        MvcResult res = mvc.perform(get("/api/notifications/stream").header("Last-Event-ID", String.valueOf(ids.get(1))).with(as(alice)))
                .andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        String body = res.getResponse().getContentAsString();
        assertTrue(body.contains("id:" + ids.get(2)), body);
        assertTrue(body.contains("id:" + ids.get(3)), body);
        assertFalse(body.contains("id:" + ids.get(1)), "the last seen event is not repeated");
        assertFalse(body.contains("id:" + ids.get(0)));
        assertTrue(body.contains("event:NEW_MATCH"));
        assertTrue(body.contains("notifications.match.title"), "the payload carries keys");
        assertTrue(body.indexOf("id:" + ids.get(2)) < body.indexOf("id:" + ids.get(3)), "oldest first");
        assertEquals(1, sse.connectionCount(alice.getId()));

        // no header: connected, nothing replayed. A header that is not one of our ids is ignored
        MvcResult fresh = mvc.perform(get("/api/notifications/stream").with(as(alice))).andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
        assertFalse(fresh.getResponse().getContentAsString().contains("id:"));
        mvc.perform(get("/api/notifications/stream").header("Last-Event-ID", "not-a-number").with(as(alice))).andExpect(MockMvcResultMatchers.request().asyncStarted());
        assertEquals(3, sse.connectionCount(alice.getId()));

        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Circle pledge notifier writes a real inbox row for the requester and says so")
    void pledgeNotifier() throws Exception {
        assertTrue(pledgeNotifier.pledgeReceived(55L, bob.getId(), "Nimal", "O+", 3));

        JsonNode items = data(mvc.perform(get("/api/notifications").with(as(bob))).andExpect(status().isOk()).andReturn()).get("items");
        assertEquals(1, items.size());
        JsonNode n = items.get(0);
        assertEquals("CIRCLE_PLEDGE", n.get("type").asText());
        assertEquals("notifications.pledge.body", n.get("bodyKey").asText());
        assertEquals("Nimal", n.get("params").get("volunteerName").asText());
        assertEquals(3, n.get("params").get("pledgeCount").asInt());
        assertEquals(55, n.get("referenceId").asInt());
        assertTrue(n.get("body").asText().contains("Nimal"));
        assertNull(n.get("params").get("phone"));
    }
}
