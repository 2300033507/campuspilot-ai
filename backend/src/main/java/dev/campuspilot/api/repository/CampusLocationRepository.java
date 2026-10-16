package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.CampusLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampusLocationRepository extends JpaRepository<CampusLocation, UUID> {

    List<CampusLocation> findAllByOrderByNameAsc();
}