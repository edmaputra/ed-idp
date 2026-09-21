package io.github.edmaputra.edidp.claims;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing {@link ClaimInclusionRule} entities.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Repository
public interface ClaimInclusionRuleRepository extends JpaRepository<ClaimInclusionRule, String> {

  List<ClaimInclusionRule> findByAttributeKeyIn(Collection<String> attributeKeys);

  List<ClaimInclusionRule> findByTenantIdAndAttributeKeyIn(String tenantId, Collection<String> attributeKeys);

  Optional<ClaimInclusionRule> findByTenantIdAndAttributeKey(String tenantId, String attributeKey);

  boolean existsByAttributeKey(String attributeKey);

  boolean existsByTenantIdAndAttributeKey(String tenantId, String attributeKey);
}