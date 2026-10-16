package dev.campuspilot.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A campus landmark that can appear on the navigation map.
 *
 * <p>Completely separate from attendance. This entity has no foreign key
 * to anything academic — a landmark belongs to the campus, not to a
 * student or a subject.
 *
 * <p>latitude and longitude are WGS84 decimal degrees, which is what both
 * OpenStreetMap tiles and the OSRM routing service expect. Storing them
 * in that one standard avoids a conversion layer nobody would remember
 * to apply.
 */
@Entity
@Table(name = "campus_locations")
@Getter
@Setter
@NoArgsConstructor
public class CampusLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category", nullable = false)
    private String category = "other";

    @Column(name = "building")
    private String building;

    @Column(name = "latitude", nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false)
    private BigDecimal longitude;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}