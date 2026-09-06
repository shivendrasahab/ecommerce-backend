package com.shivendra.ecommerce.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A login session. The opaque token is what the client sends back in the Authorization header.
 */
@Entity
@Table(name = "sessions", indexes = @Index(name = "idx_sessions_token", columnList = "token", unique = true))
@Getter
@Setter
public class Session extends BaseModel {

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.ACTIVE;
}
