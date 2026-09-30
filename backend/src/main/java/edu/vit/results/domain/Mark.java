package edu.vit.results.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** One student's MSE and ESE marks (each out of 100) in one subject. Either may still be blank. */
@Entity
@Table(name = "mark", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "subject_id"}))
public class Mark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    private Double mse;
    private Double ese;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(length = 120)
    private String updatedBy;

    protected Mark() {}

    public Mark(AppUser student, Subject subject) {
        this.student = student;
        this.subject = subject;
    }

    public Long getId() { return id; }
    public AppUser getStudent() { return student; }
    public Subject getSubject() { return subject; }
    public Double getMse() { return mse; }
    public Double getEse() { return ese; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    public void update(Double mse, Double ese, String by) {
        this.mse = mse;
        this.ese = ese;
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }
}
