package dev.mars.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Loads a {@link PipelineConfig} from a YAML resource on the classpath.
 *
 * <p>The default resource path is {@code pipeline.yaml} at the root of
 * the classpath ({@code src/main/resources/pipeline.yaml}). An
 * alternative path can be specified via the
 * {@code pipeline.config} system property, e.g.:
 * <pre>
 * java -Dpipeline.config=my-config.yaml -jar app.jar
 * </pre>
 *
 * @see PipelineConfig
 */
public final class PipelineConfigLoader {

  private static final Logger LOG = Logger.getLogger(PipelineConfigLoader.class.getName());
  private static final String DEFAULT_RESOURCE = "pipeline.yaml";
  private static final String SYSTEM_PROPERTY  = "pipeline.config";

  private PipelineConfigLoader() {}

  /**
   * Load configuration from the classpath.
   *
   * @return a fully-populated {@link PipelineConfig}
   * @throws IllegalStateException if the resource is missing or unparseable
   */
  public static PipelineConfig load() {
    String resource = System.getProperty(SYSTEM_PROPERTY, DEFAULT_RESOURCE);
    return load(resource);
  }

  /**
   * Load configuration from a specific classpath resource.
   *
   * @param resource the classpath resource path
   * @return a fully-populated {@link PipelineConfig}
   * @throws IllegalStateException if the resource is missing or unparseable
   */
  public static PipelineConfig load(String resource) {
    LOG.info("Loading pipeline config from classpath: " + resource);

    try (InputStream is = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(resource)) {

      if (is == null) {
        LOG.severe("Pipeline config not found on classpath: " + resource);
        throw new IllegalStateException(
            "Pipeline config not found on classpath: " + resource);
      }

      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
      PipelineConfig config = mapper.readValue(is, PipelineConfig.class);
      LOG.info("Pipeline config loaded: handlers=" + config.handlers().size()
          + " tools=" + config.tools().size()
          + " llm=" + config.llm().type()
          + " schema.caseIdField=" + config.schema().caseIdField());
      return config;

    } catch (IOException e) {
      LOG.severe("Failed to parse pipeline config: " + resource + " — " + e.getMessage());
      throw new IllegalStateException("Failed to parse pipeline config: " + resource, e);
    }
  }
}
