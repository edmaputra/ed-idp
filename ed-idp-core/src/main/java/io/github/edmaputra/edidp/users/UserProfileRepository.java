package io.github.edmaputra.edidp.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for managing {@link UserProfile} entities.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

  Optional<UserProfile> findByUsername(String username);

  Optional<UserProfile> findByTenantAndUsername(String tenant, String username);
}
