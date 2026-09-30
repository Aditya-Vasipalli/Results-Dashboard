package edu.vit.results.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Always stored lower-case so lookups are case-insensitive. */
    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    /** Permanent Registration Number; only students have one. */
    @Column(unique = true, length = 20)
    private String prn;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {}

    public AppUser(String name, String email, String passwordHash, Role role, String prn) {
        this.name = name;
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
        this.prn = prn;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getPrn() { return prn; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email.toLowerCase(); }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setRole(Role role) { this.role = role; }
    public void setPrn(String prn) { this.prn = prn; }
}
