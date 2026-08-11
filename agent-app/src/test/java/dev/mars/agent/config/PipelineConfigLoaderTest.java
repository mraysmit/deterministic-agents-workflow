package dev.mars.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineConfigLoaderTest {

  @Test
  void loads_default_pipeline_yaml() {
    PipelineConfig cfg = PipelineConfigLoader.load("pipeline.yaml");
    assertNotNull(cfg);
    assertEquals("trade.failures", cfg.addresses().inbound());
    assertEquals("agent.required", cfg.addresses().agent());
    assertEquals("events.out", cfg.addresses().events());
  }

  @Test
  void yaml_http_section_is_parsed() {
    PipelineConfig cfg = PipelineConfigLoader.load("pipeline.yaml");
    assertEquals(8080, cfg.http().port());
    assertEquals("/trade/failures", cfg.http().route());
    assertEquals(10_000, cfg.http().requestTimeoutMs());
  }

  @Test
  void yaml_schema_section_is_parsed() {
    PipelineConfig cfg = PipelineConfigLoader.load("pipeline.yaml");
    assertEquals("tradeId", cfg.schema().caseIdField());
    assertTrue(cfg.schema().allowedFields().contains("tradeId"));
    assertTrue(cfg.schema().allowedFields().contains("reason"));
    assertTrue(cfg.schema().requiredFields().contains("tradeId"));
    assertTrue(cfg.schema().requiredFields().contains("reason"));
  }

  @Test
  void yaml_agent_section_is_parsed() {
    PipelineConfig cfg = PipelineConfigLoader.load("pipeline.yaml");
    assertEquals(8, cfg.agent().maxSteps());
    assertEquals(10_000, cfg.agent().timeoutMs());
  }

  @Test
  void yaml_handlers_are_parsed() {
    PipelineConfig cfg = PipelineConfigLoader.load("pipeline.yaml");
    assertEquals(2, cfg.handlers().size());

    var first = cfg.handlers().get(0);
    assertEquals("Missing ISIN", first.reason());
    assertEquals("lookup-enrich", first.type());
    assertEquals("ISIN", first.params().get("identifier"));

    var second = cfg.handlers().get(1);
    assertEquals("Invalid Counterparty", second.reason());
    assertEquals("escalate", second.type());
  }

  @Test
  void yaml_tools_are_parsed() {
    PipelineConfig cfg = PipelineConfigLoader.load("pipeline.yaml");
    assertEquals(5, cfg.tools().size());
    assertEquals("publish-event", cfg.tools().get(0).type());
    assertEquals("raise-ticket", cfg.tools().get(1).type());
    assertEquals("lookup", cfg.tools().get(2).type());
    assertEquals("classify", cfg.tools().get(3).type());
    assertEquals("notify", cfg.tools().get(4).type());
  }

  @Test
  void yaml_llm_is_parsed() {
    PipelineConfig cfg = PipelineConfigLoader.load("pipeline.yaml");
    assertEquals("stub", cfg.llm().type());
  }

  @Test
  void missing_resource_throws() {
    var ex = assertThrows(IllegalStateException.class,
        () -> PipelineConfigLoader.load("nonexistent.yaml"));
    assertTrue(ex.getMessage().contains("nonexistent.yaml"));
  }
}
