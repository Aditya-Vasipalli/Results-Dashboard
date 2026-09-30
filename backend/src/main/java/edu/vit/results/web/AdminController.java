package edu.vit.results.web;

import edu.vit.results.config.AuthUser;
import edu.vit.results.domain.Role;
import edu.vit.results.service.AdminService;
import edu.vit.results.service.AuditService;
import edu.vit.results.service.MarkService;
import edu.vit.results.service.ResultService;
import edu.vit.results.service.Views.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record UserRequest(
            @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 200) String email,
            @Size(max = 100, message = "Password is too long") String password,
            @NotNull(message = "Choose a role") Role role,
            @Size(max = 20, message = "PRN is too long") String prn) {}

    public record SubjectRequest(
            @NotBlank(message = "Code is required") @Size(max = 20, message = "Code is too long") String code,
            @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name,
            @Min(value = 1, message = "Credits must be between 1 and 10") @Max(value = 10, message = "Credits must be between 1 and 10") int credits,
            Long teacherId) {}

    public record PublishRequest(@NotNull Boolean published) {}

    private final AdminService admin;
    private final ResultService results;
    private final MarkService marks;
    private final AuditService audit;

    public AdminController(AdminService admin, ResultService results, MarkService marks, AuditService audit) {
        this.admin = admin;
        this.results = results;
        this.marks = marks;
        this.audit = audit;
    }

    @GetMapping("/stats")
    public Stats stats() {
        return admin.stats();
    }

    @GetMapping("/results")
    public List<SemesterResult> results() {
        return results.allResults();
    }

    @GetMapping("/results/{studentId}")
    public SemesterResult result(@PathVariable Long studentId) {
        return results.semesterResult(studentId);
    }

    @PutMapping("/publication")
    public Map<String, Boolean> publish(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PublishRequest body) {
        return Map.of("published", admin.setPublished(me, body.published()));
    }

    @GetMapping("/users")
    public List<UserView> users(@RequestParam(required = false) Role role) {
        return admin.listUsers(role);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UserRequest body) {
        return admin.createUser(me, toInput(body));
    }

    @PutMapping("/users/{id}")
    public UserView updateUser(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody UserRequest body) {
        return admin.updateUser(me, id, toInput(body));
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Boolean> deleteUser(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        admin.deleteUser(me, id);
        return Map.of("ok", true);
    }

    @GetMapping("/subjects")
    public List<SubjectView> subjects(@AuthenticationPrincipal AuthUser me) {
        return marks.subjectsFor(me);
    }

    @PutMapping("/subjects/{id}")
    public SubjectView updateSubject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody SubjectRequest body) {
        return admin.updateSubject(me, id, new AdminService.SubjectInput(body.code(), body.name(), body.credits(), body.teacherId()));
    }

    @GetMapping("/audit")
    public List<AuditView> audit(@RequestParam(defaultValue = "50") int limit) {
        return audit.recent(limit);
    }

    private static AdminService.UserInput toInput(UserRequest b) {
        return new AdminService.UserInput(b.name(), b.email(), b.password(), b.role(), b.prn());
    }
}
