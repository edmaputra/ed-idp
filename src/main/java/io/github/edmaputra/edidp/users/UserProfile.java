package io.github.edmaputra.edidp.users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "locale", nullable = false, length = 35)
    private String locale;

    @Column(name = "zoneinfo", nullable = false, length = 100)
    private String zoneinfo;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "tenant", nullable = false, length = 100)
    private String tenant;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @OneToMany(
        mappedBy = "userProfile",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY)
    private List<UserProfileAttribute> attributes = new ArrayList<>();

    public UserProfile(
            String username,
            String fullName,
            String email,
            boolean emailVerified,
            String locale,
            String zoneinfo,
            String department,
            String tenant,
            long updatedAt) {
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.emailVerified = emailVerified;
        this.locale = locale;
        this.zoneinfo = zoneinfo;
        this.department = department;
        this.tenant = tenant;
        this.updatedAt = updatedAt;
    }

}
