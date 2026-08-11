package dev.mars.agent.memory;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryMemoryStoreTest {

  private InMemoryMemoryStore store;

  @BeforeEach
  void setUp() {
    store = new InMemoryMemoryStore();
  }

  @Test
  void load_new_case_returns_initial_state() {
    var state = store.load("case-1").result();
    assertNotNull(state);
    assertEquals("case-1", state.getString("caseId"));
    assertEquals(0, state.getInteger("step"));
    assertNotNull(state.getLong("createdAt"));
  }

  @Test
  void load_returns_copy_not_original() {
    var state1 = store.load("case-1").result();
    var state2 = store.load("case-1").result();
    state1.put("modified", true);
    assertNull(state2.getBoolean("modified"));
  }

  @Test
  void append_increments_step() {
    store.append("case-1", new JsonObject().put("action", "step0")).result();
    var state = store.load("case-1").result();
    assertEquals(1, state.getInteger("step"));

    store.append("case-1", new JsonObject().put("action", "step1")).result();
    state = store.load("case-1").result();
    assertEquals(2, state.getInteger("step"));
  }

  @Test
  void append_stores_last_entry() {
    var entry = new JsonObject().put("tool", "events.publish");
    store.append("case-1", entry).result();

    var state = store.load("case-1").result();
    assertEquals("events.publish", state.getJsonObject("last").getString("tool"));
  }

  @Test
  void append_sets_updated_at() {
    store.append("case-1", new JsonObject()).result();
    var state = store.load("case-1").result();
    assertNotNull(state.getLong("updatedAt"));
  }

  @Test
  void different_cases_are_isolated() {
    // Initialise both cases (load creates the initial state)
    store.load("case-1").result();
    store.load("case-2").result();

    store.append("case-1", new JsonObject().put("data", "A")).result();
    store.append("case-2", new JsonObject().put("data", "B")).result();

    var state1 = store.load("case-1").result();
    var state2 = store.load("case-2").result();

    assertEquals("case-1", state1.getString("caseId"));
    assertEquals("case-2", state2.getString("caseId"));
    assertEquals(1, state1.getInteger("step"));
    assertEquals(1, state2.getInteger("step"));
  }

  @Test
  void append_returns_succeeded_future() {
    var future = store.append("case-1", new JsonObject());
    assertTrue(future.succeeded());
  }

  @Test
  void load_returns_succeeded_future() {
    var future = store.load("case-1");
    assertTrue(future.succeeded());
  }
}
