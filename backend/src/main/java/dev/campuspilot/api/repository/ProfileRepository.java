package dev.campuspilot.api.repository;

import dev.campuspilot.api.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByAuthUserId(UUID authUserId);

    Optional<Profile> findByEmail(String email);
}