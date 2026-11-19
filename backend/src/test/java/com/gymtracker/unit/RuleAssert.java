package com.gymtracker.unit;

import com.gymtracker.service.ApiException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Asserts that a call breaks a business rule: ApiException with the expected rule ID (and HTTP status). */
final class RuleAssert {

    private RuleAssert() {
    }

    static void breaksRule(String expectedCode, ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo(expectedCode);
    }

    static void breaksRule(String expectedCode, HttpStatus expectedStatus, ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    org.assertj.core.api.Assertions.assertThat(api.getCode()).isEqualTo(expectedCode);
                    org.assertj.core.api.Assertions.assertThat(api.getStatus()).isEqualTo(expectedStatus);
                });
    }
}
