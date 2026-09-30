package edu.vit.results.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_entry")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored by name so the log survives the user being deleted. */
    @Column(nullable = false, length = 120)
    private String actor;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(nullable = false, length = 500)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditEntry() {}

    public AuditEntry(String actor, String action, String detail) {
        this.actor = actor;
        this.action = action;
        this.detail = detail.length() > 500 ? detail.substring(0, 500) : detail;
    }

    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
