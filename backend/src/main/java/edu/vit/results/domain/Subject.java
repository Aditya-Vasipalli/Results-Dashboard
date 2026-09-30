package edu.vit.results.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "subject")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int credits;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private AppUser teacher;

    protected Subject() {}

    public Subject(String code, String name, int credits, AppUser teacher) {
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.teacher = teacher;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getCredits() { return credits; }
    public AppUser getTeacher() { return teacher; }

    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setCredits(int credits) { this.credits = credits; }
    public void setTeacher(AppUser teacher) { this.teacher = teacher; }

    public boolean isTaughtBy(Long userId) {
        return teacher != null && teacher.getId().equals(userId);
    }
}
