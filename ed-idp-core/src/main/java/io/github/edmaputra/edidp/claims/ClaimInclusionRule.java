package io.github.edmaputra.edidp.claims;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JPA entity defining target projection rules for dynamic user claims.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Entity
@Table(name = "claim_inclusion_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimInclusionRule {

  @Column(name = "tenant_id", nullable = false, length = 100)
  private String tenantId;

  @Id
  @Column(name = "attribute_key", nullable = false, length = 120)
  private String attributeKey;

  @Column(name = "targets", nullable = false, length = 300)
  private String targets;

  public ClaimInclusionRule(String attributeKey) {
    this("demo", attributeKey);
  }

  public ClaimInclusionRule(String tenantId, String attributeKey) {
    this.tenantId = tenantId;
    this.attributeKey = attributeKey;
    this.targets = "";
  }

  public boolean includesTarget(ClaimTarget target) {
    if (targets == null || targets.isBlank()) {
      return false;
    }

    String expected = target.name();
    return Arrays.stream(targets.split(","))
        .map(String::trim)
        .filter((value) -> !value.isEmpty())
        .anyMatch(expected::equals);
  }

  public void addTarget(ClaimTarget target) {
    Set<String> allTargets = Arrays.stream((targets == null ? "" : targets).split(","))
        .map(String::trim)
        .filter((value) -> !value.isEmpty())
        .collect(Collectors.toSet());

    allTargets.add(target.name().toUpperCase(Locale.ROOT));
    this.targets = allTargets.stream().sorted().collect(Collectors.joining(","));
  }
}