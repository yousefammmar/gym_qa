package com.gymtracker.unit;

import com.gymtracker.service.UserValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.gymtracker.unit.RuleAssert.breaksRule;
import static org.assertj.core.api.Assertions.assertThat;

/** Registration validation: equivalence partitioning + boundary values (AUTH-01, 02, 03, 09). */
@DisplayName("Registration validation (UserValidator)")
class UserValidatorTest {

    @Nested
    @DisplayName("AUTH-01 username: 3-30 chars, letters/digits/_")
    class Username {

        @ParameterizedTest(name = "valid: \"{0}\"")
        @ValueSource(strings = {"abc", "user_01", "ABC_def_123", "abcdefghijklmnopqrstuvwxyz1234"}) // 3 and 30 chars = boundaries
        void acceptsValidUsernames(String username) {
            assertThat(UserValidator.username(username)).isEqualTo(username);
        }

        @Test
        void trimsSurroundingSpaces() {
            assertThat(UserValidator.username("  lifter  ")).isEqualTo("lifter");
        }

        @ParameterizedTest(name = "invalid: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"ab", "abcdefghijklmnopqrstuvwxyz12345", "ab c", "ab-c", "émile", "user@1"}) // 2 and 31 chars
        void rejectsInvalidUsernames(String username) {
            breaksRule("AUTH-01", () -> UserValidator.username(username));
        }
    }

    @Nested
    @DisplayName("AUTH-02 email: name@domain.tld, max 100")
    class Email {

        @Test
        void acceptsAndLowerCasesValidEmail() {
            assertThat(UserValidator.email("  Lifter@Gym.IO ")).isEqualTo("lifter@gym.io");
        }

        @Test
        void acceptsExactly100Characters() {
            String email = "a".repeat(94) + "@ex.io";
            assertThat(email).hasSize(100);
            assertThat(UserValidator.email(email)).isEqualTo(email);
        }

        @Test
        void rejects101Characters() {
            String email = "a".repeat(95) + "@ex.io";
            breaksRule("AUTH-02", () -> UserValidator.email(email));
        }

        @ParameterizedTest(name = "invalid: \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"gym.io", "a@b", "a b@c.de", "@gym.io", "a@.io", "a@@b.io"})
        void rejectsMalformedEmails(String email) {
            breaksRule("AUTH-02", () -> UserValidator.email(email));
        }
    }

    @Nested
    @DisplayName("AUTH-03 password: 8-64 chars, >= 1 letter AND >= 1 digit")
    class Password {

        @Test
        void boundaryLengths() {
            breaksRule("AUTH-03", () -> UserValidator.password("abcdef1"));             // 7
            UserValidator.password("abcdefg1");                                          // 8
            UserValidator.password("a".repeat(63) + "1");                                // 64
            breaksRule("AUTH-03", () -> UserValidator.password("a".repeat(64) + "1"));  // 65
            breaksRule("AUTH-03", () -> UserValidator.password(null));
        }

        /** Multiple condition coverage of (hasLetter, hasDigit) for a valid length. */
        @Test
        void letterAndDigitCombinations() {
            UserValidator.password("abcdefg1");                                    // T, T
            breaksRule("AUTH-03", () -> UserValidator.password("abcdefgh"));      // T, F
            breaksRule("AUTH-03", () -> UserValidator.password("12345678"));      // F, T
            breaksRule("AUTH-03", () -> UserValidator.password("!!!!!!!!"));      // F, F
        }

        /** Found by mutation testing: a symbol must not count as a digit. */
        @Test
        void symbolIsNotADigit() {
            breaksRule("AUTH-03", () -> UserValidator.password("abcdefg!"));
            breaksRule("AUTH-03", () -> UserValidator.password("pass word"));
        }

        @Test
        void spacesAndSymbolsAreAllowed() {
            UserValidator.password(" pass word 9 ");
        }
    }

    @Nested
    @DisplayName("AUTH-09 display name: optional, max 50")
    class DisplayName_ {

        @Test
        void blankUsesFallback() {
            assertThat(UserValidator.displayName("   ", "lifter")).isEqualTo("lifter");
            assertThat(UserValidator.displayName(null, "lifter")).isEqualTo("lifter");
        }

        @Test
        void boundaries() {
            assertThat(UserValidator.displayName("x".repeat(50), "f")).hasSize(50);
            breaksRule("AUTH-09", () -> UserValidator.displayName("x".repeat(51), "f"));
        }
    }
}
