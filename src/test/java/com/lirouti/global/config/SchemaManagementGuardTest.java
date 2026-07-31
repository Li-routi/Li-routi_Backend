package com.lirouti.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchemaManagementGuard 테스트")
class SchemaManagementGuardTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"validate", " validate ", "VALIDATE"})
    @DisplayName("validate이거나 값이 없으면 우회가 아니다 — 미주입은 기본값이 validate라 경고할 일이 없다")
    void isOverridden_ValidateOrBlank_False(String ddlAuto) {
        assertThat(SchemaManagementGuard.isOverridden(ddlAuto)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "create", "create-drop", "none"})
    @DisplayName("validate가 아닌 값은 전부 우회로 본다 — none도 검증을 끄는 것이라 경고 대상이다")
    void isOverridden_AnythingElse_True(String ddlAuto) {
        assertThat(SchemaManagementGuard.isOverridden(ddlAuto)).isTrue();
    }
}
