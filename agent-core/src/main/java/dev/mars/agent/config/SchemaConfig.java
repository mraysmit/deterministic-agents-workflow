package dev.mars.agent.config;

import java.util.Set;

/**
 * Schema / field-validation configuration for incoming events.
 *
 * @param caseIdField    the JSON field used as the case identifier
 *                       (e.g. {@code "tradeId"})
 * @param allowedFields  field names forwarded downstream after sanitisation
 * @param requiredFields field names that must be present (subset of
 *                       {@code allowedFields})
 */
public record SchemaConfig(
    String caseIdField,
    Set<String> allowedFields,
    Set<String> requiredFields
) {
  public SchemaConfig {
    allowedFields = Set.copyOf(allowedFields);
    requiredFields = Set.copyOf(requiredFields);
  }
}
