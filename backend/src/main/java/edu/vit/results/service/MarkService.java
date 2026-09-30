package edu.vit.results.service;

import edu.vit.results.config.AuthUser;
import edu.vit.results.domain.AppUser;
import edu.vit.results.domain.Mark;
import edu.vit.results.domain.Role;
import edu.vit.results.domain.Subject;
import edu.vit.results.repo.MarkRepository;
import edu.vit.results.repo.SubjectRepository;
import edu.vit.results.repo.UserRepository;
import edu.vit.results.service.Views.*;
import edu.vit.results.web.ApiException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marks entry for teachers; read-only gradebook access for admins. */
@Service
public class MarkService {

    public record Entry(Long studentId, Double mse, Double ese) {}

    private final SubjectRepository subjects;
    private final UserRepository users;
    private final MarkRepository marks;
    private final AuditService audit;

    public MarkService(SubjectRepository subjects, UserRepository users, MarkRepository marks, AuditService audit) {
        this.subjects = subjects;
        this.users = users;
        this.marks = marks;
        this.audit = audit;
    }

    /** Teachers see the subjects they teach; admins see all of them. */
    @Transactional(readOnly = true)
    public List<SubjectView> subjectsFor(AuthUser me) {
        List<Subject> list = me.isAdmin()
                ? subjects.findAllByOrderByCodeAsc()
                : subjects.findAllByTeacherIdOrderByCodeAsc(me.id());
        long students = users.countByRole(Role.STUDENT);
        return list.stream().map(s -> SubjectView.of(s, students, countComplete(s.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public Gradebook gradebook(AuthUser me, Long subjectId) {
        Subject s = load(subjectId);
        boolean owns = s.isTaughtBy(me.id());
        if (!owns && !me.isAdmin()) throw ApiException.forbidden("You do not teach this subject");

        Map<Long, Mark> byStudent = marks.findAllBySubjectId(subjectId).stream()
                .collect(Collectors.toMap(m -> m.getStudent().getId(), Function.identity()));
        List<AppUser> students = users.findAllByRoleOrderByPrnAsc(Role.STUDENT);
        List<GradebookRow> rows = students.stream().map(st -> {
            Mark m = byStudent.get(st.getId());
            SubjectResult r = ResultService.subjectResult(s, m);
            return new GradebookRow(st.getId(), st.getPrn(), st.getName(), r.mse(), r.ese(), r.total(),
                    r.grade(), r.status(), m == null ? null : m.getUpdatedAt(), m == null ? null : m.getUpdatedBy());
        }).toList();
        long complete = rows.stream().filter(r -> !r.status().equals("PENDING")).count();
        return new Gradebook(SubjectView.of(s, students.size(), complete), owns, rows);
    }

    /** Upserts marks for one subject. A row with both marks null removes that student's marks. */
    @Transactional
    public SaveResult save(AuthUser me, Long subjectId, List<Entry> entries) {
        Subject s = load(subjectId);
        if (!s.isTaughtBy(me.id())) throw ApiException.forbidden("Only the subject teacher can enter marks");

        Set<Long> seen = new HashSet<>();
        for (Entry e : entries) {
            if (e.studentId() == null) throw ApiException.badRequest("Each row needs a student");
            if (!seen.add(e.studentId())) throw ApiException.badRequest("The same student appears twice in one save");
            check("MSE", e.mse());
            check("ESE", e.ese());
        }

        Map<Long, AppUser> students = users.findAllById(seen).stream()
                .filter(u -> u.getRole() == Role.STUDENT)
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        for (Long id : seen) {
            if (!students.containsKey(id)) throw ApiException.badRequest("Student #" + id + " does not exist");
        }

        Map<Long, Mark> existing = marks.findAllBySubjectId(subjectId).stream()
                .collect(Collectors.toMap(m -> m.getStudent().getId(), Function.identity()));
        int saved = 0, cleared = 0;
        for (Entry e : entries) {
            Double mse = round(e.mse()), ese = round(e.ese());
            Mark m = existing.get(e.studentId());
            if (mse == null && ese == null) {
                if (m != null) {
                    marks.delete(m);
                    cleared++;
                }
                continue;
            }
            if (m == null) m = new Mark(students.get(e.studentId()), s);
            m.update(mse, ese, me.name());
            marks.save(m);
            saved++;
        }
        audit.log(me, "marks.save", s.getCode() + ": " + saved + " saved, " + cleared + " cleared");
        return new SaveResult(saved, cleared);
    }

    private static void check(String label, Double v) {
        if (v == null) return;
        if (v.isNaN() || v.isInfinite() || v < 0 || v > GradingPolicy.MAX_MARKS) {
            throw ApiException.badRequest(label + " marks must be between 0 and 100");
        }
    }

    private static Double round(Double v) {
        return v == null ? null : GradingPolicy.round2(v);
    }

    private long countComplete(Long subjectId) {
        return marks.findAllBySubjectId(subjectId).stream()
                .filter(m -> m.getMse() != null && m.getEse() != null)
                .count();
    }

    private Subject load(Long id) {
        return subjects.findById(id).orElseThrow(() -> ApiException.notFound("Subject not found"));
    }
}
