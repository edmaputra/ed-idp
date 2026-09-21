package io.github.edmaputra.edidp.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_profile_attributes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileAttribute {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "username", nullable = false)
  private UserProfile userProfile;

  @Column(name = "tenant_id", nullable = false, length = 100)
  private String tenantId;

  @Column(name = "attribute_key", nullable = false, length = 120)
  private String attributeKey;

  @Column(name = "attribute_value", nullable = false, length = 1000)
  private String attributeValue;

  public UserProfileAttribute(
      UserProfile userProfile,
      String attributeKey,
      String attributeValue) {
    this.userProfile = userProfile;
    this.tenantId = userProfile.getTenant();
    this.attributeKey = attributeKey;
    this.attributeValue = attributeValue;
  }
}
