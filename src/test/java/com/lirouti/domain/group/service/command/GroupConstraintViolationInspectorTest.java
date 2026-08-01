package com.lirouti.domain.group.service.command;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("그룹 DB unique 제약 판별 테스트")
class GroupConstraintViolationInspectorTest {
    @Test
    @DisplayName("스키마 접두사가 있는 제약 이름도 판별한다")
    void isUniqueConstraintViolation_SchemaQualifiedName_ReturnsTrue() {
        DataIntegrityViolationException exception = violation(
                "lirouti." + GroupDatabaseConstraints.ROUTINE_CATEGORY_NAME,
                ConstraintViolationException.ConstraintKind.UNIQUE
        );

        assertThat(GroupConstraintViolationInspector.isUniqueConstraintViolation(
                exception,
                GroupDatabaseConstraints.ROUTINE_CATEGORY_NAME
        )).isTrue();
    }

    @Test
    @DisplayName("다른 제약과 UNIQUE가 아닌 위반은 거부한다")
    void isUniqueConstraintViolation_DifferentOrNonUnique_ReturnsFalse() {
        assertThat(GroupConstraintViolationInspector.isUniqueConstraintViolation(
                violation(
                        GroupDatabaseConstraints.ROUTINE_TITLE,
                        ConstraintViolationException.ConstraintKind.UNIQUE
                ),
                GroupDatabaseConstraints.ROUTINE_CATEGORY_NAME
        )).isFalse();
        assertThat(GroupConstraintViolationInspector.isUniqueConstraintViolation(
                violation(
                        GroupDatabaseConstraints.ROUTINE_CATEGORY_NAME,
                        ConstraintViolationException.ConstraintKind.OTHER
                ),
                GroupDatabaseConstraints.ROUTINE_CATEGORY_NAME
        )).isFalse();
    }

    private DataIntegrityViolationException violation(
            String constraintName,
            ConstraintViolationException.ConstraintKind kind
    ) {
        SQLException sqlException = new SQLException("constraint violation", "23000", 1062);
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "constraint violation",
                sqlException,
                kind,
                constraintName
        );
        return new DataIntegrityViolationException("constraint violation", constraintViolation);
    }
}
