package dev.campuspilot.api.web;

import dev.campuspilot.api.config.CurrentProfileResolver;
import dev.campuspilot.api.service.SubjectService;
import dev.campuspilot.api.web.dto.CreateSubjectRequest;
import dev.campuspilot.api.web.dto.SubjectResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Which subjects you see depends on your role, and that decision is made
 * once in SubjectService rather than at each call site.
 *
 * <p>Creating, editing and deleting require faculty or admin — enforced
 * in the service. Hiding the buttons is only a convenience on top.
 */
@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjects;
    private final CurrentProfileResolver currentProfile;

    public SubjectController(SubjectService subjects, CurrentProfileResolver currentProfile) {
        this.subjects = subjects;
        this.currentProfile = currentProfile;
    }

    @GetMapping
    public List<SubjectResponse> list() {
        return subjects.listVisibleTo(currentProfile.resolve());
    }

    @GetMapping("/{id}")
    public SubjectResponse get(@PathVariable UUID id) {
        return subjects.get(currentProfile.resolve(), id);
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody CreateSubjectRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subjects.create(currentProfile.requireFacultyOrAdmin(), body));
    }

    @PutMapping("/{id}")
    public SubjectResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody CreateSubjectRequest body) {
        return subjects.update(currentProfile.requireFacultyOrAdmin(), id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        subjects.delete(currentProfile.requireFacultyOrAdmin(), id);
        return ResponseEntity.noContent().build();
    }
}