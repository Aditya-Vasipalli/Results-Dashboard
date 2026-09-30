package edu.vit.results.service;

import edu.vit.results.config.AuthUser;
import edu.vit.results.domain.AppUser;
import edu.vit.results.domain.Role;
import edu.vit.results.domain.Setting;
import edu.vit.results.domain.Subject;
import edu.vit.results.repo.MarkRepository;
import edu.vit.results.repo.SettingRepository;
import edu.vit.results.repo.SubjectRepository;
import edu.vit.results.repo.UserRepository;
import edu.vit.results.service.GradingPolicy.Grade;
import edu.vit.results.service.Views.*;
import edu.vit.results.web.ApiException;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    public record UserInput(String name, String email, String password, Role role, String prn) {}
    public record SubjectInput(String code, String name, int credits, Long teacherId) {}

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final MarkRepository marks;
    private final SettingRepository settings;
    private final ResultService results;
    private final AuditService audit;
    private final PasswordEncoder encoder;

    public AdminService(UserRepository users, SubjectRepository subjects, MarkRepository marks,
                        SettingRepository settings, ResultService results, AuditService audit, PasswordEncoder encoder) {
        this.users = users;
        this.subjects = subjects;
        this.marks = marks;
        this.settings = settings;
        this.results = results;
        this.audit = audit;
        this.encoder = encoder;
    }

    // ---------------------------------------------------------------- users

    @Transactional(readOnly = true)
    public List<UserView> listUsers(Role role) {
        List<AppUser> list = role == null ? users.findAllByOrderByRoleAscNameAsc() : users.findAllByRoleOrderByNameAsc(role);
        return list.stream().map(UserView::of).toList();
    }

    @Transactional
    public UserView createUser(AuthUser me, UserInput in) {
        if (in.password() == null || in.password().length() < 6) throw ApiException.badRequest("Password must be at least 6 characters");
        String email = in.email().trim().toLowerCase();
        String prn = normalisePrn(in.role(), in.prn());
        ensureUnique(email, prn, null);
        AppUser u = users.save(new AppUser(in.name().trim(), email, encoder.encode(in.password()), in.role(), prn));
        audit.log(me, "user.create", "Created " + label(u));
        return UserView.of(u);
    }

    @Transactional
    public UserView updateUser(AuthUser me, Long id, UserInput in) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getId().equals(me.id()) && in.role() != Role.ADMIN) {
            throw ApiException.badRequest("You cannot remove your own admin access");
        }
        String email = in.email().trim().toLowerCase();
        String prn = normalisePrn(in.role(), in.prn());
        ensureUnique(email, prn, u.getId());

        Role before = u.getRole();
        if (before != in.role()) {
            // Keep the data consistent when someone changes role.
            if (before == Role.TEACHER) subjects.unassignTeacher(u.getId());
            if (before == Role.STUDENT) marks.deleteAllForStudent(u.getId());
        }
        u.setName(in.name().trim());
        u.setEmail(email);
        u.setRole(in.role());
        u.setPrn(prn);
        boolean reset = in.password() != null && !in.password().isEmpty();
        if (reset) {
            if (in.password().length() < 6) throw ApiException.badRequest("Password must be at least 6 characters");
            u.setPasswordHash(encoder.encode(in.password()));
        }
        audit.log(me, "user.update", "Updated " + label(u) + (reset ? " (password reset)" : ""));
        return UserView.of(u);
    }

    @Transactional
    public void deleteUser(AuthUser me, Long id) {
        if (id.equals(me.id())) throw ApiException.badRequest("You cannot delete your own account");
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getRole() == Role.TEACHER) subjects.unassignTeacher(u.getId());
        if (u.getRole() == Role.STUDENT) marks.deleteAllForStudent(u.getId());
        users.delete(u);
        audit.log(me, "user.delete", "Deleted " + label(u));
    }

    private String normalisePrn(Role role, String prn) {
        if (role != Role.STUDENT) return null;
        String p = prn == null ? "" : prn.trim().toUpperCase();
        if (p.isEmpty()) throw ApiException.badRequest("Students need a PRN");
        if (!p.matches("[A-Z0-9]{4,20}")) throw ApiException.badRequest("PRN may only contain letters and digits (4-20 characters)");
        return p;
    }

    private void ensureUnique(String email, String prn, Long selfId) {
        users.findByEmail(email).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
            throw ApiException.conflict("That email is already registered");
        });
        if (prn != null) {
            boolean taken = users.findAllByRoleOrderByPrnAsc(Role.STUDENT).stream()
                    .anyMatch(o -> prn.equals(o.getPrn()) && !o.getId().equals(selfId));
            if (taken) throw ApiException.conflict("Another student already has PRN " + prn);
        }
    }

    private static String label(AppUser u) {
        String who = u.getRole().name().toLowerCase() + " " + u.getName() + " (" + u.getEmail() + ")";
        return u.getPrn() == null ? who : who + ", PRN " + u.getPrn();
    }

    // ------------------------------------------------------------- subjects

    @Transactional
    public SubjectView updateSubject(AuthUser me, Long id, SubjectInput in) {
        Subject s = subjects.findById(id).orElseThrow(() -> ApiException.notFound("Subject not found"));
        String code = in.code().trim().toUpperCase();
        if (!code.matches("[A-Z0-9-]{2,20}")) throw ApiException.badRequest("Code may only contain letters, digits and dashes");
        subjects.findAllByOrderByCodeAsc().stream()
                .filter(o -> o.getCode().equals(code) && !o.getId().equals(id))
                .findAny()
                .ifPresent(o -> { throw ApiException.conflict("Another subject already uses code " + code); });

        AppUser teacher = null;
        if (in.teacherId() != null) {
            teacher = users.findById(in.teacherId())
                    .filter(u -> u.getRole() == Role.TEACHER)
                    .orElseThrow(() -> ApiException.badRequest("Selected teacher does not exist"));
        }
        s.setCode(code);
        s.setName(in.name().trim());
        s.setCredits(in.credits());
        s.setTeacher(teacher);
        audit.log(me, "subject.update", code + " " + s.getName() + ", " + in.credits() + " credits, teacher: "
                + (teacher == null ? "none" : teacher.getName()));
        long students = users.countByRole(Role.STUDENT);
        long complete = marks.findAllBySubjectId(id).stream().filter(m -> m.getMse() != null && m.getEse() != null).count();
        return SubjectView.of(s, students, complete);
    }

    // ---------------------------------------------------------- publication

    @Transactional
    public boolean setPublished(AuthUser me, boolean published) {
        Setting s = settings.findById(Setting.RESULTS_PUBLISHED).orElseGet(() -> new Setting(Setting.RESULTS_PUBLISHED, "false"));
        s.setValue(Boolean.toString(published));
        settings.save(s);
        audit.log(me, published ? "results.publish" : "results.withdraw",
                published ? "Published semester results to students" : "Withdrew semester results from students");
        return published;
    }

    // ---------------------------------------------------------------- stats

    @Transactional(readOnly = true)
    public Stats stats() {
        List<SemesterResult> all = results.allResults();
        List<Subject> subjectList = subjects.findAllByOrderByCodeAsc();

        List<SubjectStat> bySubject = new ArrayList<>();
        for (int i = 0; i < subjectList.size(); i++) {
            Subject s = subjectList.get(i);
            final int idx = i;
            List<SubjectResult> rows = all.stream().map(r -> r.subjects().get(idx)).toList();
            List<SubjectResult> done = rows.stream().filter(r -> !r.status().equals("PENDING")).toList();
            Map<String, Long> grades = new LinkedHashMap<>();
            for (Grade g : Grade.values()) grades.put(g.label, 0L);
            done.forEach(r -> grades.merge(r.grade(), 1L, Long::sum));
            Double avg = done.isEmpty() ? null : GradingPolicy.round2(done.stream().mapToDouble(SubjectResult::total).average().orElse(0));
            Double passRate = done.isEmpty() ? null
                    : GradingPolicy.round2(100.0 * done.stream().filter(r -> r.status().equals("PASS")).count() / done.size());
            bySubject.add(new SubjectStat(s.getId(), s.getCode(), s.getName(), s.getCredits(),
                    s.getTeacher() == null ? null : s.getTeacher().getName(), rows.size(), done.size(), avg, passRate, grades));
        }

        long passed = all.stream().filter(r -> r.status().equals("PASS")).count();
        long failed = all.stream().filter(r -> r.status().equals("FAIL")).count();
        OptionalDouble avgSgpa = all.stream().filter(r -> r.sgpa() != null).mapToDouble(SemesterResult::sgpa).average();
        return new Stats(
                all.size(),
                users.countByRole(Role.TEACHER),
                subjectList.size(),
                marks.countComplete(),
                (long) all.size() * subjectList.size(),
                results.isPublished(),
                passed,
                failed,
                all.size() - passed - failed,
                avgSgpa.isPresent() ? GradingPolicy.round2(avgSgpa.getAsDouble()) : null,
                bySubject);
    }
}
