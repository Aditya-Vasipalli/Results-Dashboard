package edu.vit.results.service;

import edu.vit.results.domain.AppUser;
import edu.vit.results.domain.AuditEntry;
import edu.vit.results.domain.Role;
import edu.vit.results.domain.Subject;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Read models returned by the API. Kept as records so they serialize predictably. */
public final class Views {

    private Views() {}

    public record StudentInfo(Long id, String name, String prn, String email) {
        static StudentInfo of(AppUser u) {
            return new StudentInfo(u.getId(), u.getName(), u.getPrn(), u.getEmail());
        }
    }

    /** status is PASS, FAIL or PENDING (a mark is still missing). */
    public record SubjectResult(
            Long subjectId, String code, String name, int credits, String teacher,
            Double mse, Double ese, Double total, String grade, Integer gradePoints, String status) {}

    public record SemesterResult(
            StudentInfo student, List<SubjectResult> subjects, int totalCredits,
            Integer creditsEarned, Double sgpa, Double percentage, String status) {}

    public record StudentResult(boolean published, StudentInfo student, SemesterResult result) {}

    public record SubjectView(
            Long id, String code, String name, int credits, Long teacherId, String teacherName,
            long studentCount, long completeCount) {
        static SubjectView of(Subject s, long students, long complete) {
            var t = s.getTeacher();
            return new SubjectView(s.getId(), s.getCode(), s.getName(), s.getCredits(),
                    t == null ? null : t.getId(), t == null ? null : t.getName(), students, complete);
        }
    }

    public record GradebookRow(
            Long studentId, String prn, String name, Double mse, Double ese, Double total,
            String grade, String status, Instant updatedAt, String updatedBy) {}

    public record Gradebook(SubjectView subject, boolean canEdit, List<GradebookRow> rows) {}

    public record SaveResult(int saved, int cleared) {}

    public record UserView(Long id, String name, String email, Role role, String prn, Instant createdAt) {
        public static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getPrn(), u.getCreatedAt());
        }
    }

    public record AuditView(Long id, String actor, String action, String detail, Instant createdAt) {
        public static AuditView of(AuditEntry e) {
            return new AuditView(e.getId(), e.getActor(), e.getAction(), e.getDetail(), e.getCreatedAt());
        }
    }

    public record SubjectStat(
            Long id, String code, String name, int credits, String teacher, long students, long complete,
            Double average, Double passRate, Map<String, Long> grades) {}

    public record Stats(
            long students, long teachers, int subjects, long marksComplete, long marksExpected,
            boolean published, long passed, long failed, long pending, Double averageSgpa,
            List<SubjectStat> bySubject) {}
}
