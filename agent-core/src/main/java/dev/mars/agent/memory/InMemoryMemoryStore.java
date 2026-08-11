package dev.mars.agent.memory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Non-persistent {@link MemoryStore} backed by {@link ConcurrentHashMap}s.
 *
 * <p>Suitable for local development, testing, and single-instance deployments.
 * All data is lost on process restart. For production, replace with a durable
 * implementation backed by a database or distributed cache.
 *
 * <h2>Thread safety</h2>
 * Both {@link #load} and {@link #append} use {@code ConcurrentHashMap.compute}
 * to ensure atomicity of read-modify-write operations. {@link #load} returns
 * a defensive <em>copy</em> of the state so the caller cannot mutate the
 * canonical store.
 *
 * @see MemoryStore
 */
public class InMemoryMemoryStore implements MemoryStore {

  private static final Logger LOG = Logger.getLogger(InMemoryMemoryStore.class.getName());

  private final Map<String, JsonObject> stateByCase = new ConcurrentHashMap<>();
  private final Map<String, JsonArray> logByCase = new ConcurrentHashMap<>();

  @Override
  public Future<JsonObject> load(String caseId) {
    JsonObject state = stateByCase.computeIfAbsent(caseId, k -> {
      LOG.info("Memory: new case state created for caseId=" + caseId);
      return new JsonObject()
        .put("caseId", caseId)
        .put("createdAt", System.currentTimeMillis())
        .put("step", 0);
    });
    LOG.fine("Memory: loaded state for caseId=" + caseId + " step=" + state.getInteger("step", 0));
    return Future.succeededFuture(state.copy());
  }

  @Override
  public Future<Void> append(String caseId, JsonObject entry) {
    logByCase.compute(caseId, (k, existing) -> {
      JsonArray log = existing == null ? new JsonArray() : existing;
      log.add(entry);
      return log;
    });
    stateByCase.compute(caseId, (k, old) -> {
      JsonObject s = old == null ? new JsonObject() : old.copy();
      int step = s.getInteger("step", 0);
      s.put("step", step + 1);
      s.put("last", entry);
      s.put("updatedAt", System.currentTimeMillis());
      LOG.info("Memory: appended entry for caseId=" + caseId + " newStep=" + (step + 1));
      return s;
    });
    return Future.succeededFuture();
  }
}
