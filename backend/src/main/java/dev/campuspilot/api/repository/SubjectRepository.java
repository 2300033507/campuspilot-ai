package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    /**
     * Scoped by profileId on purpose. Every read is filtered to one owner,
     * so a caller who guesses another student's subject id still gets
     * nothing back — the filter lives in the query, not in the caller.
     */
    List<Subject> findByProfileIdOrderByNameAsc(UUID profileId);

    Optional<Subject> findByIdAndProfileId(UUID id, UUID profileId);

    boolean existsByProfileIdAndNameIgnoreCase(UUID profileId, String name);
}