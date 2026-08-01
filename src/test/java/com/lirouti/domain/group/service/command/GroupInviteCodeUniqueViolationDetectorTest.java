package com.lirouti.domain.group.service.command;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("초대코드 unique 제약 판별 테스트")
class GroupInviteCodeUniqueViolationDetectorTest {
    private final GroupInviteCodeUniqueViolationDetector detector =
            new GroupInviteCodeUniqueViolationDetector();

    @Test
    @DisplayName("초대코드 unique 제약 위반만 true로 판별한다")
    void isInviteCodeUniqueViolation_InviteCodeConstraint_ReturnsTrue() {
        assertThat(detector.isInviteCodeUniqueViolation(
                violation(GroupDatabaseConstraints.INVITE_CODE)
        )).isTrue();
        assertThat(detector.isInviteCodeUniqueViolation(
                violation("lirouti." + GroupDatabaseConstraints.INVITE_CODE)
        )).isTrue();
    }

    @Test
    @DisplayName("바깥쪽의 다른 unique 위반 뒤에 있는 초대코드 위반도 찾는다")
    void isInviteCodeUniqueViolation_NestedAfterOtherUnique_ReturnsTrue() {
        ConstraintViolationException inviteViolation = constraintViolation(
                GroupDatabaseConstraints.INVITE_CODE,
                new SQLException("duplicate invite code", "23000", 1062)
        );
        ConstraintViolationException otherViolation = constraintViolation(
                GroupDatabaseConstraints.ROUTINE_TITLE,
                new SQLException("nested unique violation", inviteViolation)
        );

        assertThat(detector.isInviteCodeUniqueViolation(
                new DataIntegrityViolationException("outer", otherViolation)
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
        return new DataIntegrityViolationException(
                "duplicate value",
                constraintViolation(constraintName, sqlException)
        );
    }

    private ConstraintViolationException constraintViolation(
            String constraintName,
            SQLException cause
    ) {
        return new ConstraintViolationException(
                "duplicate value",
                cause,
                ConstraintViolationException.ConstraintKind.UNIQUE,
                constraintName
        );
    }
}
