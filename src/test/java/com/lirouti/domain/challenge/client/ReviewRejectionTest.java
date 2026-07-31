package com.lirouti.domain.challenge.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 반려 종류 해석.
 *
 * 값이 없거나 모르는 값일 때 어느 쪽으로 접는지가 이 클래스의 전부다.
 * MISMATCH 로 접으면 유해 반려가 불일치로 집계되어 "심사가 유해성을 얼마나 걸렀는가"를
 * 볼 수 없게 된다.
 */
@DisplayName("반려 종류 해석 테스트")
class ReviewRejectionTest {

    @ParameterizedTest(name = "\"{0}\" → MISMATCH")
    @ValueSource(strings = {"MISMATCH", "mismatch", "  MisMatch  "})
    @DisplayName("대소문자·공백이 달라도 알아본다")
    void from_KnownValue_IsParsed(String raw) {
        assertThat(ReviewRejection.from(raw)).isEqualTo(ReviewRejection.MISMATCH);
        assertThat(ReviewRejection.isKnown(raw)).isTrue();
    }

    @ParameterizedTest(name = "{0} → UNSAFE")
    @NullSource
    @ValueSource(strings = {"", "   ", "INAPPROPRIATE", "UNKNOWN", "NONE"})
    @DisplayName("없거나 모르는 값은 안전한 쪽으로 접는다 — 유해 반려가 불일치로 묻히면 안 된다")
    void from_UnknownValue_FallsBackToUnsafe(String raw) {
        assertThat(ReviewRejection.from(raw)).isEqualTo(ReviewRejection.UNSAFE);
        assertThat(ReviewRejection.isKnown(raw)).isFalse();
    }

    @Test
    @DisplayName("문자열이 아닌 값도 안전한 쪽으로 접는다")
    void from_NonString_FallsBackToUnsafe() {
        assertThat(ReviewRejection.from(42)).isEqualTo(ReviewRejection.UNSAFE);
        assertThat(ReviewRejection.isKnown(42)).isFalse();
    }
}
