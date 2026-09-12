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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionRepository sessionRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, sessionRepository, 60_000L);
    }

    @Test
    void signupHashesPasswordAndLowercasesEmail() {
        SignupRequestDto request = new SignupRequestDto();
        request.setName("Shivendra");
        request.setEmail("Shivendra@Example.com");
        request.setPassword("secret123");
        when(userRepository.existsByEmailIgnoreCase("Shivendra@Example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = authService.signup(request);

        assertThat(saved.getEmail()).isEqualTo("shivendra@example.com");
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(new BCryptPasswordEncoder().matches("secret123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void signupRejectsDuplicateEmail() {
        SignupRequestDto request = new SignupRequestDto();
        request.setEmail("dup@example.com");
        request.setPassword("secret123");
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request)).isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginCreatesSessionAndExpiresOlderOnes() {
        User user = user("a@example.com", "secret123");
        Session old = new Session();
        old.setStatus(SessionStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserAndStatus(user, SessionStatus.ACTIVE)).thenReturn(List.of(old));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginRequestDto request = new LoginRequestDto();
        request.setEmail("a@example.com");
        request.setPassword("secret123");
        LoginResponseDto response = authService.login(request);

        assertThat(old.getStatus()).isEqualTo(SessionStatus.EXPIRED);
        assertThat(response.token()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(response.expiresAt()).isAfter(Instant.now());
        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getToken()).isEqualTo(response.token());
    }

    @Test
    void loginWithWrongPasswordIs401() {
        User user = user("a@example.com", "secret123");
        when(userRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.of(user));

        LoginRequestDto request = new LoginRequestDto();
        request.setEmail("a@example.com");
        request.setPassword("wrong");
        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void validateTokenExpiresStaleSession() {
        Session session = new Session();
        session.setStatus(SessionStatus.ACTIVE);
        session.setExpiresAt(Instant.now().minusSeconds(5));
        session.setUser(new User());
        when(sessionRepository.findByToken("t")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.validateToken("t")).isInstanceOf(UnauthorizedException.class);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.EXPIRED);
    }

    @Test
    void validateTokenReturnsUserForActiveSession() {
        User user = new User();
        Session session = new Session();
        session.setStatus(SessionStatus.ACTIVE);
        session.setExpiresAt(Instant.now().plusSeconds(60));
        session.setUser(user);
        when(sessionRepository.findByToken("t")).thenReturn(Optional.of(session));

        assertThat(authService.validateToken("t")).isSameAs(user);
    }

    private static User user(String email, String rawPassword) {
        User user = new User();
        user.setEmail(email);
        user.setName("Test");
        user.setPasswordHash(new BCryptPasswordEncoder().encode(rawPassword));
        return user;
    }
}
