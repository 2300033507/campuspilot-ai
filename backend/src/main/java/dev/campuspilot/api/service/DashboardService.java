package dev.campuspilot.api.service;

import dev.campuspilot.api.projection.DashboardRow;
import dev.campuspilot.api.repository.DashboardQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dashboard.
 *
 * <p>Note what this class does not contain: any arithmetic. There is no
 * division, no floor, no percentage calculation anywhere in this file.
 * Every number was produced by attendance_health() inside PostgreSQL
 * before these rows were ever read.
 *
 * <p>That is the design decision worth defending. If the same formula
 * existed in both Java and SQL, the two copies would eventually disagree,
 * and nobody would know which one the student was looking at. Keeping
 * one implementation removes the possibility of disagreement.
 */
@Service
public class DashboardService {

    private final DashboardQueryRepository dashboard;

    public DashboardService(DashboardQueryRepository dashboard) {
        this.dashboard = dashboard;
    }

    @Transactional(readOnly = true)
    public List<DashboardRow> forProfile(UUID profileId) {
        return dashboard.findDashboardForProfile(profileId);
    }

    /**
     * Summary counts per status, so the UI can show "2 subjects need
     * attention" without walking the rows itself.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> statusCounts(UUID profileId) {
        return forProfile(profileId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        DashboardRow::getStatus,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
    }
}