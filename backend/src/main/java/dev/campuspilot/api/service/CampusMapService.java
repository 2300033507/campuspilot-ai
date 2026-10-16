package dev.campuspilot.api.service;

import dev.campuspilot.api.domain.CampusLocation;
import dev.campuspilot.api.exception.RoutingUnavailableException;
import dev.campuspilot.api.repository.CampusLocationRepository;
import dev.campuspilot.api.web.dto.CampusLocationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Campus wayfinding. Read-only with respect to attendance — touches none
 * of it.
 *
 * <p>WHY THE JDK HTTP CLIENT, NOT SPRING'S RestClient
 *   RestClient is the natural choice, and it was the first one here. It
 *   failed at response-extraction time for every target type, including
 *   String, because Boot 4 ships Jackson 3 while RestClient's default
 *   converters are still built for Jackson 2. The same class of version
 *   mismatch already broke jjwt-jackson once in this project.
 *
 *   java.net.http.HttpClient is in the JDK, has no message converters,
 *   no Jackson involvement, and no version coupling at all. For a single
 *   upstream call that returns JSON, that is the smaller dependency
 *   rather than the larger one.
 */
@Service
public class CampusMapService {

    /**
     * The public OSRM demo server.
     *
     * <p>Free, no API key. But it is rate-limited and explicitly NOT for
     * production. A real deployment self-hosts OSRM or buys a plan —
     * routing is exactly the kind of thing that degrades under load on a
     * shared demo server.
     */
    private static final String OSRM = "https://router.project-osrm.org/route/v1/foot";

    private final CampusLocationRepository locations;
    private final HttpClient http;
    private final tools.jackson.databind.ObjectMapper json;

    public CampusMapService(CampusLocationRepository locations,
                            tools.jackson.databind.ObjectMapper json) {
        this.locations = locations;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CampusLocationResponse> allLocations() {
        return locations.findAllByOrderByNameAsc().stream()
                .map(CampusLocationResponse::from)
                .toList();
    }

    /**
     * Walking route between two coordinates.
     *
     * <p>Coordinates go out as longitude,latitude — GeoJSON order, and
     * the reverse of what the rest of this codebase uses. Every map
     * library wants lat,lng; OSRM wants lon,lat. Getting it wrong yields
     * a plausible-looking route in the wrong hemisphere, so it is
     * called out rather than left as a detail to remember.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> walkingRoute(double fromLat, double fromLng,
                                            double toLat, double toLng) {

        String url = OSRM + "/" + fromLng + "," + fromLat + ";"
                           + toLng + "," + toLat
                + "?overview=full&geometries=geojson";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        String raw;
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            raw = response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();   // restore the flag before giving up
            throw new RoutingUnavailableException("Routing call was interrupted", ex);
        } catch (Exception ex) {
            throw new RoutingUnavailableException("Could not reach the routing service", ex);
        }

        try {
            Map<String, Object> parsed = json.readValue(raw, Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            throw new RoutingUnavailableException(
                    "The routing service returned an unreadable response", ex);
        }
    }
}