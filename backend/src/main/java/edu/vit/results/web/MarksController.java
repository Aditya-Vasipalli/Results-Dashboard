package edu.vit.results.web;

import edu.vit.results.config.AuthUser;
import edu.vit.results.service.MarkService;
import edu.vit.results.service.ResultService;
import edu.vit.results.service.Views.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MarksController {

    public record MarkEntry(@NotNull(message = "Each row needs a student") Long studentId, Double mse, Double ese) {}

    public record SaveMarks(@NotNull @Size(max = 5000, message = "Too many rows in one save") List<@Valid @NotNull MarkEntry> entries) {}

    private final MarkService marks;
    private final ResultService results;

    public MarksController(MarkService marks, ResultService results) {
        this.marks = marks;
        this.results = results;
    }

    // ---- teachers (own subjects) and admins (read-only)

    @GetMapping("/subjects")
    public List<SubjectView> subjects(@AuthenticationPrincipal AuthUser me) {
        return marks.subjectsFor(me);
    }

    @GetMapping("/subjects/{id}/marks")
    public Gradebook gradebook(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return marks.gradebook(me, id);
    }

    @PutMapping("/subjects/{id}/marks")
    public SaveResult save(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody SaveMarks body) {
        var entries = body.entries().stream().map(e -> new MarkService.Entry(e.studentId(), e.mse(), e.ese())).toList();
        return marks.save(me, id, entries);
    }

    // ---- students

    @GetMapping("/student/result")
    public StudentResult myResult(@AuthenticationPrincipal AuthUser me) {
        return results.forStudent(me.id());
    }
}
