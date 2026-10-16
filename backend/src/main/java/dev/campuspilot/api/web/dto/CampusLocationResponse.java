package dev.campuspilot.api.web.dto;

import dev.campuspilot.api.domain.CampusLocation;

import java.util.UUID;

public record CampusLocationResponse(
        UUID id,
        String name,
        String description,
        String category,
        String building,
        double latitude,
        double longitude
) {

    /**
     * Doubles rather than BigDecimal on the way out.
     *
     * <p>JSON has no decimal type, and the client wants a number it can
     * hand straight to a map library. Converting here means every
     * consumer gets the same shape and no BigDecimal-to-double question
     * gets re-asked at each call site.
     */
    public static CampusLocationResponse from(CampusLocation l) {
        return new CampusLocationResponse(
                l.getId(),
                l.getName(),
                l.getDescription(),
                l.getCategory(),
                l.getBuilding(),
                l.getLatitude().doubleValue(),
                l.getLongitude().doubleValue()
        );
    }
}