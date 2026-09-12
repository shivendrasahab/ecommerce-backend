package com.shivendra.ecommerce.services;

import com.shivendra.ecommerce.dtos.LoginRequestDto;
import com.shivendra.ecommerce.dtos.LoginResponseDto;
import com.shivendra.ecommerce.dtos.SignupRequestDto;
import com.shivendra.ecommerce.exceptions.BadRequestException;
import com.shivendra.ecommerce.exceptions.UnauthorizedException;
import com.shivendra.ecommerce.models.Session;
import com.shivendra.ecommerce.models.SessionStatus;
import com.shivendra.ecommerce.models.User;
import com.shivendra.ecommerce.repositories.SessionRepository;
import com.shivendra.ecommerce.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Signup / login / logout with BCrypt-hashed passwords and opaque session tokens stored in the DB.
 * Tokens are 32 random bytes from SecureRandom, URL-safe base64 encoded.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final long sessionTtlMillis;

    public AuthService(UserRepository userRepository,
                       SessionRepository sessionRepository,
                       @Value("${auth.session-ttl-millis:172800000}") long sessionTtlMillis) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.sessionTtlMillis = sessionTtlMillis;
    }

    @Transactional
    public User signup(SignupRequestDto request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("An account with this email already exists");
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail().toLowerCase());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        return userRepository.save(user);
    }

    @Transactional
    public LoginResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // one active session per user: expire older ones
        for (Session old : sessionRepository.findByUserAndStatus(user, SessionStatus.ACTIVE)) {
            old.setStatus(SessionStatus.EXPIRED);
            sessionRepository.save(old);
        }

        Session session = new Session();
        session.setUser(user);
        session.setToken(generateToken());
        session.setExpiresAt(Instant.now().plusMillis(sessionTtlMillis));
        session.setStatus(SessionStatus.ACTIVE);
        sessionRepository.save(session);

        return new LoginResponseDto(session.getToken(), user.getEmail(), user.getName(), session.getExpiresAt());
    }

    @Transactional
    public void logout(String token) {
        sessionRepository.findByToken(token).ifPresent(session -> {
            session.setStatus(SessionStatus.LOGGED_OUT);
            sessionRepository.save(session);
        });
    }

    /** Resolves a token to its user, or throws 401. Lazily expires stale sessions. */
    @Transactional
    public User validateToken(String token) {
        Session session = sessionRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid session token"));

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new UnauthorizedException("Session is no longer active");
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus(SessionStatus.EXPIRED);
            sessionRepository.save(session);
            throw new UnauthorizedException("Session has expired, please log in again");
        }
        return session.getUser();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
