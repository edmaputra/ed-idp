package io.github.edmaputra.edidp.claims;

/**
 * Immutable value object representing a single dynamic user attribute key-value pair.
 *
 * @param key   the attribute key name, must not be null or blank
 * @param value the attribute value
 * @author edmaputra
 * @since 0.0.1
 */
public record UserAttributeData(String key, String value) {

  public UserAttributeData {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Attribute key must not be null or blank");
    }
  }
}
