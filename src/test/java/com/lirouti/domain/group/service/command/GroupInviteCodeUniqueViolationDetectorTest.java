package com.lirouti.domain.group.service.command;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("초대코드 unique 제약 판별 테스트")
class GroupInviteCodeUniqueViolationDetectorTest {
    private static final String INVITE_CODE_UNIQUE_CONSTRAINT =
            "UKmgt3kl7whp0n031hlo8x6jupi";

    private final GroupInviteCodeUniqueViolationDetector detector =
            new GroupInviteCodeUniqueViolationDetector();

    @Test
    @DisplayName("초대코드 unique 제약 위반만 true로 판별한다")
    void isInviteCodeUniqueViolation_InviteCodeConstraint_ReturnsTrue() {
        assertThat(detector.isInviteCodeUniqueViolation(
                violation(INVITE_CODE_UNIQUE_CONSTRAINT)
        )).isTrue();
        assertThat(detector.isInviteCodeUniqueViolation(
                violation("lirouti." + INVITE_CODE_UNIQUE_CONSTRAINT)
        )).isTrue();
    }

    @Test
    @DisplayName("다른 unique 제약이나 일반 무결성 오류는 false로 판별한다")
    void isInviteCodeUniqueViolation_OtherIntegrityViolation_ReturnsFalse() {
        assertThat(detector.isInviteCodeUniqueViolation(
                violation("uk_group_routine_group_title")
        )).isFalse();
        assertThat(detector.isInviteCodeUniqueViolation(
                new DataIntegrityViolationException("not-null violation")
        )).isFalse();
    }

    private DataIntegrityViolationException violation(String constraintName) {
        SQLException sqlException = new SQLException("duplicate value", "23000", 1062);
        ConstraintViolationException constraintViolationException = new ConstraintViolationException(
                "duplicate value",
                sqlException,
                ConstraintViolationException.ConstraintKind.UNIQUE,
                constraintName
        );
        return new DataIntegrityViolationException("duplicate value", constraintViolationException);
    }
}
