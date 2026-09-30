package edu.vit.results.service;

import edu.vit.results.domain.AppUser;
import edu.vit.results.domain.Mark;
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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns stored marks into subject results and semester results. */
@Service
@Transactional(readOnly = true)
public class ResultService {

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final MarkRepository marks;
    private final SettingRepository settings;

    public ResultService(UserRepository users, SubjectRepository subjects, MarkRepository marks, SettingRepository settings) {
        this.users = users;
        this.subjects = subjects;
        this.marks = marks;
        this.settings = settings;
    }

    public boolean isPublished() {
        return settings.findById(Setting.RESULTS_PUBLISHED).map(s -> "true".equals(s.getValue())).orElse(false);
    }

    public StudentResult forStudent(Long studentId) {
        AppUser student = users.findById(studentId).orElseThrow(() -> ApiException.notFound("Student not found"));
        if (!isPublished()) return new StudentResult(false, StudentInfo.of(student), null);
        return new StudentResult(true, StudentInfo.of(student), semesterResult(studentId));
    }

    public SemesterResult semesterResult(Long studentId) {
        AppUser student = users.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> ApiException.notFound("Student not found"));
        Map<Long, Mark> bySubject = marks.findAllByStudentId(studentId).stream()
                .collect(Collectors.toMap(m -> m.getSubject().getId(), m -> m));
        return build(student, subjects.findAllByOrderByCodeAsc(), bySubject);
    }

    /** Every student's result, ordered by PRN. */
    public List<SemesterResult> allResults() {
        List<Subject> subjectList = subjects.findAllByOrderByCodeAsc();
        Map<Long, Map<Long, Mark>> byStudent = new HashMap<>();
        for (Mark m : marks.findAll()) {
            byStudent.computeIfAbsent(m.getStudent().getId(), k -> new HashMap<>()).put(m.getSubject().getId(), m);
        }
        return users.findAllByRoleOrderByPrnAsc(Role.STUDENT).stream()
                .map(s -> build(s, subjectList, byStudent.getOrDefault(s.getId(), Map.of())))
                .toList();
    }

    public static SubjectResult subjectResult(Subject s, Mark m) {
        Double mse = m == null ? null : m.getMse();
        Double ese = m == null ? null : m.getEse();
        String teacher = s.getTeacher() == null ? null : s.getTeacher().getName();
        if (mse == null || ese == null) {
            return new SubjectResult(s.getId(), s.getCode(), s.getName(), s.getCredits(), teacher,
                    mse, ese, null, null, null, "PENDING");
        }
        double total = GradingPolicy.total(mse, ese);
        Grade g = GradingPolicy.grade(total, ese);
        return new SubjectResult(s.getId(), s.getCode(), s.getName(), s.getCredits(), teacher,
                mse, ese, total, g.label, g.points, g == Grade.F ? "FAIL" : "PASS");
    }

    private SemesterResult build(AppUser student, List<Subject> subjectList, Map<Long, Mark> bySubject) {
        List<SubjectResult> rows = subjectList.stream().map(s -> subjectResult(s, bySubject.get(s.getId()))).toList();
        int totalCredits = rows.stream().mapToInt(SubjectResult::credits).sum();
        boolean complete = !rows.isEmpty() && rows.stream().noneMatch(r -> r.status().equals("PENDING"));
        if (!complete) {
            return new SemesterResult(StudentInfo.of(student), rows, totalCredits, null, null, null, "PENDING");
        }
        int weighted = rows.stream().mapToInt(r -> r.credits() * r.gradePoints()).sum();
        int earned = rows.stream().filter(r -> r.status().equals("PASS")).mapToInt(SubjectResult::credits).sum();
        double sgpa = totalCredits == 0 ? 0 : GradingPolicy.round2((double) weighted / totalCredits);
        double pct = GradingPolicy.round2(rows.stream().mapToDouble(SubjectResult::total).average().orElse(0));
        boolean failed = rows.stream().anyMatch(r -> r.status().equals("FAIL"));
        return new SemesterResult(StudentInfo.of(student), rows, totalCredits, earned, sgpa, pct, failed ? "FAIL" : "PASS");
    }
}
