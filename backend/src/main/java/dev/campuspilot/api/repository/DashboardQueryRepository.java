package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.Subject;
import dev.campuspilot.api.projection.DashboardRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the dashboard view.
 *
 * <p>This is the deliberate step outside JPA. The view already computes
 * attendance health per subject using CROSS JOIN LATERAL, so asking
 * Hibernate to rebuild that in Java would mean fetching every attendance
 * row and re-doing the arithmetic in the application — slower, and
 * duplicate logic that could drift from the database copy.
 *
 * <p>One round trip returns the entire dashboard.
 *
 * <p>NOTE ON THE GENERIC PARAMETER
 * The domain type must be a managed JPA entity, so it is Subject, not
 * DashboardRow. Declaring it as Repository&lt;DashboardRow, UUID&gt;
 * fails at startup with "Not a managed type" — Spring Data tries to look
 * the projection up in the metamodel and cannot find it, because a
 * projection is an interface describing a result set, not something
 * Hibernate persists.
 *
 * That restriction applies to the repository's domain type only.
 * Individual methods are still free to return projections.
 */
public interface DashboardQueryRepository extends Repository<Subject, UUID> {

    @Query(value = """
            SELECT  v.subject_id       AS "subjectId",
                    v.subject_name     AS "subjectName",
                    v.subject_code     AS "subjectCode",
                    v.semester         AS "semester",
                    v.target_attendance AS "targetAttendance",
                    v.remaining_classes_estimate AS "remainingClassesEstimate",
                    v.total_held       AS "totalHeld",
                    v.attended         AS "attended",
                    v.missed           AS "missed",
                    v.pct              AS "pct",
                    v.can_skip         AS "canSkip",
                    v.must_attend      AS "mustAttend",
                    v.recoverable      AS "recoverable",
                    v.status           AS "status",
                    v.last_class       AS "lastClass"
            FROM v_student_dashboard v
            JOIN subjects s ON s.id = v.subject_id
            WHERE s.profile_id = :profileId
            ORDER BY CASE v.status
                        WHEN 'IMPOSSIBLE' THEN 1
                        WHEN 'CRITICAL'   THEN 2
                        WHEN 'AT_RISK'    THEN 3
                        WHEN 'ON_TRACK'   THEN 4
                        ELSE 5
                     END,
                     v.subject_name
            """, nativeQuery = true)
    List<DashboardRow> findDashboardForProfile(@Param("profileId") UUID profileId);
}