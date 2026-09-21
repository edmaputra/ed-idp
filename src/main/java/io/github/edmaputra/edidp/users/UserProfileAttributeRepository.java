package io.github.edmaputra.edidp.users;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileAttributeRepository extends JpaRepository<UserProfileAttribute, Long> {

  List<UserProfileAttribute> findByUserProfileUsername(String username);

  List<UserProfileAttribute> findByTenantIdAndUserProfileUsername(String tenantId, String username);

  boolean existsByUserProfileUsernameAndAttributeKey(String username, String attributeKey);

  boolean existsByTenantIdAndUserProfileUsernameAndAttributeKey(
      String tenantId,
      String username,
      String attributeKey);
}
