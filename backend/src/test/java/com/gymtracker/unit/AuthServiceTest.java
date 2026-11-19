package com.gymtracker.unit;

import com.gymtracker.model.AuthDtos.AuthResponse;
import com.gymtracker.model.AuthDtos.LoginRequest;
import com.gymtracker.model.AuthDtos.PasswordChangeRequest;
import com.gymtracker.model.AuthDtos.RegisterRequest;
import com.gymtracker.model.User;
import com.gymtracker.repository.UserRepository;
import com.gymtracker.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.gymtracker.unit.RuleAssert.breaksRule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Registration and login rules with a mocked repository (AUTH-03 .. AUTH-10). */
@DisplayName("Registration and login (AuthService)")
class AuthServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 0);

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost keeps tests fast
    private UserRepository users;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        auth = new AuthService(users, encoder, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private User storedUser(String plainPassword) {
        return new User(7, "lifter", "lifter@gym.io", encoder.encode(plainPassword), "Lifter", NOW.toString());
    }

    @Test
    @DisplayName("AUTH-05 register stores a BCrypt hash, never the plain password")
    void registerHashesPassword() {
        when(users.insert(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(7L);
        when(users.findById(7L)).thenReturn(Optional.of(storedUser("lift4ever")));

        AuthResponse response = auth.register(new RegisterRequest("lifter", "Lifter@Gym.io", "lift4ever", ""));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).insert(eq("lifter"), eq("lifter@gym.io"), hash.capture(), eq("lifter"), eq(NOW));
        assertThat(hash.getValue()).isNotEqualTo("lift4ever").startsWith("$2");
        assertThat(encoder.matches("lift4ever", hash.getValue())).isTrue();
        assertThat(response.token()).isNotBlank();
        verify(users).insertToken(eq(response.token()), eq(7L), eq(NOW));
    }

    @Test
    @DisplayName("AUTH-04 duplicate username -> 409")
    void registerRejectsDuplicateUsername() {
        when(users.usernameTaken("lifter")).thenReturn(true);
        breaksRule("AUTH-04", HttpStatus.CONFLICT,
                () -> auth.register(new RegisterRequest("lifter", "new@gym.io", "lift4ever", null)));
        verify(users, never()).insert(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("AUTH-04 duplicate email -> 409")
    void registerRejectsDuplicateEmail() {
        when(users.emailTaken("taken@gym.io", -1)).thenReturn(true);
        breaksRule("AUTH-04", HttpStatus.CONFLICT,
                () -> auth.register(new RegisterRequest("newbie", "taken@gym.io", "lift4ever", null)));
    }

    @Test
    @DisplayName("invalid input is rejected before the database is touched")
    void registerValidatesFirst() {
        breaksRule("AUTH-01", () -> auth.register(new RegisterRequest("ab", "a@gym.io", "lift4ever", null)));
        breaksRule("AUTH-02", () -> auth.register(new RegisterRequest("abc", "nope", "lift4ever", null)));
        breaksRule("AUTH-03", () -> auth.register(new RegisterRequest("abc", "a@gym.io", "short1", null)));
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("AUTH-06 login: blank fields -> 400")
    void loginBlankFields() {
        breaksRule("AUTH-06", HttpStatus.BAD_REQUEST, () -> auth.login(new LoginRequest(" ", "x")));
        breaksRule("AUTH-06", HttpStatus.BAD_REQUEST, () -> auth.login(new LoginRequest("lifter", null)));
        breaksRule("AUTH-06", HttpStatus.BAD_REQUEST, () -> auth.login(new LoginRequest(null, "lift4ever")));
    }

    @Test
    @DisplayName("AUTH-06 login: unknown user and wrong password give the same 401")
    void loginFailures() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(users.findByUsername("lifter")).thenReturn(Optional.of(storedUser("lift4ever")));
        breaksRule("AUTH-06", HttpStatus.UNAUTHORIZED, () -> auth.login(new LoginRequest("ghost", "lift4ever")));
        breaksRule("AUTH-06", HttpStatus.UNAUTHORIZED, () -> auth.login(new LoginRequest("lifter", "wrong123")));
        verify(users, never()).insertToken(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("AUTH-06 login: correct password returns a token")
    void loginSuccess() {
        when(users.findByUsername("lifter")).thenReturn(Optional.of(storedUser("lift4ever")));
        AuthResponse response = auth.login(new LoginRequest(" lifter ", "lift4ever"));
        assertThat(response.token()).hasSizeGreaterThan(20);
        assertThat(response.user().username()).isEqualTo("lifter");
    }

    @Test
    @DisplayName("tokens are random 256-bit values: 43 URL-safe characters, different on every login")
    void tokensAreRandom() {
        when(users.findByUsername("lifter")).thenReturn(Optional.of(storedUser("lift4ever")));
        String first = auth.login(new LoginRequest("lifter", "lift4ever")).token();
        String second = auth.login(new LoginRequest("lifter", "lift4ever")).token();
        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("AUTH-08 malformed or missing Authorization header -> 401")
    void tokenHeaderFormat() {
        breaksRule("AUTH-08", HttpStatus.UNAUTHORIZED, () -> auth.userIdForToken(null));
        breaksRule("AUTH-08", () -> auth.userIdForToken("Token abc"));
        breaksRule("AUTH-08", () -> auth.userIdForToken("Bearer "));
        when(users.findToken("unknown")).thenReturn(Optional.empty());
        breaksRule("AUTH-08", () -> auth.userIdForToken("Bearer unknown"));
    }

    @Test
    @DisplayName("AUTH-08 valid token -> user id; expired token -> 401 and deleted")
    void tokenLifetime() {
        when(users.findToken("fresh")).thenReturn(Optional.of(new UserRepository.TokenRow(7, NOW.minusDays(6))));
        when(users.findToken("old")).thenReturn(Optional.of(new UserRepository.TokenRow(7, NOW.minusDays(7))));
        assertThat(auth.userIdForToken("Bearer fresh")).isEqualTo(7);
        breaksRule("AUTH-08", () -> auth.userIdForToken("Bearer old"));
        verify(users).deleteToken("old");
    }

    @Test
    @DisplayName("token expiry boundary: 7 days minus 1 s valid, exactly 7 days expired")
    void expiryBoundary() {
        LocalDateTime issued = NOW;
        assertThat(AuthService.isExpired(issued, issued.plusDays(7).minusSeconds(1))).isFalse();
        assertThat(AuthService.isExpired(issued, issued.plusDays(7))).isTrue();
        assertThat(AuthService.isExpired(issued, issued.plusDays(7).plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("AUTH-10 change password needs the correct current password")
    void changePassword() {
        when(users.findById(7L)).thenReturn(Optional.of(storedUser("lift4ever")));
        breaksRule("AUTH-10", () -> auth.changePassword(7, "Bearer t", new PasswordChangeRequest("wrong123", "newpass99")));
        breaksRule("AUTH-10", () -> auth.changePassword(7, "Bearer t", new PasswordChangeRequest(null, "newpass99")));
        breaksRule("AUTH-03", () -> auth.changePassword(7, "Bearer t", new PasswordChangeRequest("lift4ever", "short")));
        verify(users, never()).updatePassword(anyLong(), anyString());

        auth.changePassword(7, "Bearer t", new PasswordChangeRequest("lift4ever", "newpass99"));
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).updatePassword(eq(7L), hash.capture());
        assertThat(encoder.matches("newpass99", hash.getValue())).isTrue();
        verify(users).deleteOtherTokens(7, "t");
    }
}
