package dev.campuspilot.api.web;

import dev.campuspilot.api.config.CurrentProfileResolver;
import dev.campuspilot.api.projection.DashboardRow;
import dev.campuspilot.api.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only, and available to every role — a teacher sees their own
 * attendance health on the same terms as a student.
 *
 * <p>Try it:
 *   curl -H "Authorization: Bearer TOKEN" http://localhost:8080/api/dashboard
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;
    private final CurrentProfileResolver currentProfile;

    public DashboardController(DashboardService dashboard, CurrentProfileResolver currentProfile) {
        this.dashboard = dashboard;
        this.currentProfile = currentProfile;
    }

    @GetMapping
    public List<DashboardRow> dashboard() {
        UUID profileId = currentProfile.resolveId();
        return dashboard.forProfile(profileId);
    }

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        UUID profileId = currentProfile.resolveId();
        return dashboard.statusCounts(profileId);
    }
}