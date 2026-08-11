package dev.mars.agent.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchemaConfigTest {

  @Test
  void fields_are_defensively_copied() {
    var allowed = new java.util.HashSet<>(Set.of("a", "b"));
    var required = new java.util.HashSet<>(Set.of("a"));
    var cfg = new SchemaConfig("id", allowed, required);

    // Mutating original sets should not affect the record
    allowed.add("c");
    required.add("b");

    assertEquals(Set.of("a", "b"), cfg.allowedFields());
    assertEquals(Set.of("a"), cfg.requiredFields());
  }

  @Test
  void returned_sets_are_immutable() {
    var cfg = new SchemaConfig("id", Set.of("a", "b"), Set.of("a"));
    assertThrows(UnsupportedOperationException.class,
        () -> cfg.allowedFields().add("c"));
    assertThrows(UnsupportedOperationException.class,
        () -> cfg.requiredFields().add("b"));
  }

  @Test
  void case_id_field_is_stored() {
    var cfg = new SchemaConfig("tradeId", Set.of("tradeId"), Set.of("tradeId"));
    assertEquals("tradeId", cfg.caseIdField());
  }
}
