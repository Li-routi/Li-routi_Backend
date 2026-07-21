package com.lirouti.global.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("S3Properties presigned URL 유효 시간 검증")
class S3PropertiesTest {

    private S3Properties withExpiration(Duration duration) {
        S3Properties properties = new S3Properties();
        properties.setPresignedUrlExpiration(duration);
        return properties;
    }

    @ParameterizedTest
    @CsvSource({"PT1S", "PT5M", "PT1H", "P1D", "P7D"})
    @DisplayName("1초 이상 7일 이하이면 통과한다 (7일 경계 포함)")
    void inRange_ReturnsTrue(String iso) {
        assertThat(withExpiration(Duration.parse(iso)).isPresignedUrlExpirationInRange()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S", "P8D", "P30D"})
    @DisplayName("0·음수·7일 초과이면 실패한다")
    void outOfRange_ReturnsFalse(String iso) {
        assertThat(withExpiration(Duration.parse(iso)).isPresignedUrlExpirationInRange()).isFalse();
    }

    @Test
    @DisplayName("null이면 통과시킨다 (@NotNull이 별도로 잡으므로 중복 위반 방지)")
    void nullExpiration_ReturnsTrue() {
        assertThat(withExpiration(null).isPresignedUrlExpirationInRange()).isTrue();
    }
}
