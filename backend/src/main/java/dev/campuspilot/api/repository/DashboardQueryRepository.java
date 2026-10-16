package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.Subject;
import dev.campuspilot.api.projection.DashboardRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the student dashboard.
 *
 * <p>Deliberately outside JPA. The view already computes health per
 * subject using CROSS JOIN LATERAL, so asking Hibernate to rebuild that
 * in Java would mean fetching every attendance row and redoing the
 * arithmetic in the application — slower, and duplicate logic that could
 * drift from the database copy.
 *
 * <p>One round trip returns the whole dashboard. The profile id is always
 * bound as a parameter from the verified token, so a caller cannot ask
 * for somebody else's view by changing a query argument.
 */
public interface DashboardQueryRepository extends Repository<Subject, UUID> {

    @Query(value = """
            SELECT  profile_id          AS "profileId",
                    subject_id          AS "subjectId",
                    subject_name        AS "subjectName",
                    subject_code        AS "subjectCode",
                    semester            AS "semester",
                    target_attendance   AS "targetAttendance",
                    remaining_classes_estimate AS "remainingClassesEstimate",
                    total_held          AS "totalHeld",
                    attended            AS "attended",
                    missed              AS "missed",
                    pct                 AS "pct",
                    can_skip            AS "canSkip",
                    must_attend         AS "mustAttend",
                    recoverable         AS "recoverable",
                    status              AS "status",
                    last_class          AS "lastClass"
            FROM v_attendance_health
            WHERE profile_id = :profileId
            ORDER BY CASE status
                        WHEN 'IMPOSSIBLE' THEN 1
                        WHEN 'CRITICAL'   THEN 2
                        WHEN 'AT_RISK'    THEN 3
                        WHEN 'ON_TRACK'   THEN 4
                        ELSE 5
                     END,
                     subject_name
            """, nativeQuery = true)
    List<DashboardRow> findDashboardForProfile(@Param("profileId") UUID profileId);
}