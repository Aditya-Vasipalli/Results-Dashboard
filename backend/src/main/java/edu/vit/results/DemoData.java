package edu.vit.results;

import edu.vit.results.domain.*;
import edu.vit.results.repo.*;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads a demo semester (4 subjects, 12 students) into an empty database. */
@Component
public class DemoData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final UserRepository users;
    private final SubjectRepository subjects;
    private final MarkRepository marks;
    private final AuditRepository audit;
    private final SettingRepository settings;
    private final PasswordEncoder encoder;
    private final boolean reset;

    public DemoData(UserRepository users, SubjectRepository subjects, MarkRepository marks, AuditRepository audit,
                    SettingRepository settings, PasswordEncoder encoder, @Value("${app.reset:false}") boolean reset) {
        this.users = users;
        this.subjects = subjects;
        this.marks = marks;
        this.audit = audit;
        this.settings = settings;
        this.encoder = encoder;
        this.reset = reset;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (reset) {
            marks.deleteAllInBatch();
            subjects.deleteAllInBatch();
            users.deleteAllInBatch();
            audit.deleteAllInBatch();
            settings.deleteAllInBatch();
            log.info("app.reset=true: wiped all data");
        }
        if (users.count() > 0) return;

        String adminPw = encoder.encode("admin123");
        String teacherPw = encoder.encode("teacher123");
        String studentPw = encoder.encode("student123");

        users.save(new AppUser("Dr. Ananya Rao", "admin@vit.edu", adminPw, Role.ADMIN, null));
        AppUser iyer = users.save(new AppUser("Prof. R. Iyer", "iyer@vit.edu", teacherPw, Role.TEACHER, null));
        AppUser kapoor = users.save(new AppUser("Prof. S. Kapoor", "kapoor@vit.edu", teacherPw, Role.TEACHER, null));
        AppUser fernandes = users.save(new AppUser("Prof. M. Fernandes", "fernandes@vit.edu", teacherPw, Role.TEACHER, null));
        AppUser kulkarni = users.save(new AppUser("Prof. A. Kulkarni", "kulkarni@vit.edu", teacherPw, Role.TEACHER, null));

        List<Subject> subs = subjects.saveAll(List.of(
                new Subject("CS3201", "Computer Networks", 4, iyer),
                new Subject("CS3202", "Database Management Systems", 4, kapoor),
                new Subject("CS3203", "Operating Systems", 3, fernandes),
                new Subject("CS3204", "Theory of Computation", 3, kulkarni)));

        String[] names = {
            "Aarav Mehta", "Diya Nair", "Kabir Shah", "Meera Pillai", "Rohan Das", "Sana Qureshi",
            "Tara Menon", "Vikram Joshi", "Ishaan Reddy", "Nisha Verma", "Arjun Patil", "Pooja Deshmukh",
        };
        List<AppUser> students = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            String first = names[i].split(" ")[0].toLowerCase();
            String last = names[i].split(" ")[1].toLowerCase();
            String prn = String.format("123100%02d", i + 1);
            students.add(users.save(new AppUser(names[i], first + "." + last + "@vit.edu", studentPw, Role.STUDENT, prn)));
        }

        // Deterministic pseudo-random marks so the demo looks the same every time.
        long state = 42;
        for (int si = 0; si < students.size(); si++) {
            for (int ji = 0; ji < subs.size(); ji++) {
                state = (state * 1103515245L + 12345L) & 0x7fffffffL;
                double mse = 45 + (state % 51);          // 45..95
                state = (state * 1103515245L + 12345L) & 0x7fffffffL;
                double ese = 42 + (state % 54);          // 42..95
                Double eseMark = ese;
                if (si == 5 && ji == 1) eseMark = 31.0;  // one failed subject
                if (si == 11 && ji == 3) eseMark = null; // one mark still to be entered
                Mark m = new Mark(students.get(si), subs.get(ji));
                m.update(mse, eseMark, subs.get(ji).getTeacher().getName());
                marks.save(m);
            }
        }
        settings.save(new Setting(Setting.RESULTS_PUBLISHED, "true"));
        audit.save(new AuditEntry("System", "demo.load", "Loaded the demo semester: 4 subjects, 12 students"));
        log.info("Loaded demo data. Sign in as admin@vit.edu / admin123");
    }
}
