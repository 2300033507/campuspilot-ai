package dev.campuspilot.api.web;

import dev.campuspilot.api.service.CampusMapService;
import dev.campuspilot.api.web.dto.CampusLocationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Campus navigation. Additive — none of this touches attendance.
 *
 * <p>These endpoints require a token like the rest of the API, even
 * though the map data is not personal. Keeping one rule for "what needs
 * auth" is simpler to reason about than a mixture, and the data is
 * campus-wide rather than secret.
 */
@RestController
@RequestMapping("/api/campus")
public class CampusMapController {

    private final CampusMapService map;

    public CampusMapController(CampusMapService map) {
        this.map = map;
    }

    @GetMapping("/locations")
    public List<CampusLocationResponse> locations() {
        return map.allLocations();
    }

    /**
     * Proxies OSRM.
     *
     * <p>Taking coordinates as query parameters keeps the endpoint
     * stateless and matches what a map library already has in hand — it
     * does not have to convert into a body it then forgets to send.
     */
    @GetMapping("/route")
    public Map<String, Object> route(@RequestParam double fromLat,
                                     @RequestParam double fromLng,
                                     @RequestParam double toLat,
                                     @RequestParam double toLng) {
        return map.walkingRoute(fromLat, fromLng, toLat, toLng);
    }
}