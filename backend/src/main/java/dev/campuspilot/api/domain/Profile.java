package dev.campuspilot.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A person using the app: a student, or a faculty member.
 *
 * <p>authUserId stays nullable until authentication is wired in during a
 * later step. It is the link to Supabase's auth.users table, and it is
 * the column every Row Level Security policy compares against
 * auth.uid(). Leaving it null means RLS matches nothing, which is safe —
 * it fails closed rather than open.
 */
@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "auth_user_id", unique = true)
    private UUID authUserId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", unique = true)
    private String email;

    /**
     * BCrypt hash. Never the raw password — that is the entire point of
     * hashing, and why no DTO ever exposes this field.
     *
     * <p>Null means the account has never set a password. The demo
     * profile created in Step 1 is in that state and cannot log in until
     * one is assigned.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "role", nullable = false)
    private String role = "student";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}