package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.Subject;
import dev.campuspilot.api.projection.RosterEntry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Faculty-facing roster queries.
 *
 * <p>As with DashboardQueryRepository, the generic parameter is Subject
 * because Spring Data requires a repository's domain type to be a managed
 * entity. RosterEntry is a projection describing a result set, not
 * something Hibernate persists.
 */
public interface RosterQueryRepository extends Repository<Subject, UUID> {

    @Query(value = "SELECT * FROM subject_roster(:subjectId)", nativeQuery = true)
    List<RosterEntry> rosterFor(@Param("subjectId") UUID subjectId);
}