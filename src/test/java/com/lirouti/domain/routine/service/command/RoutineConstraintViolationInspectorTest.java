package com.lirouti.domain.routine.service.command;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("개인 루틴 DB UNIQUE 제약 판별 테스트")
class RoutineConstraintViolationInspectorTest {
    private static final String CONSTRAINT = "uk_member_routine_member_template";

    @Test
    @DisplayName("DataIntegrityViolationException 안의 Hibernate UNIQUE 제약을 판별한다")
    void isUniqueConstraintViolation_HibernateUnique_ReturnsTrue() {
        DataIntegrityViolationException exception = violation(
                "lirouti." + CONSTRAINT,
                ConstraintViolationException.ConstraintKind.UNIQUE
        );

        assertThat(RoutineConstraintViolationInspector.isUniqueConstraintViolation(
                exception, CONSTRAINT)).isTrue();
    }

    @Test
    @DisplayName("Hibernate가 UNIQUE가 아니라고 명시한 예외는 메시지와 무관하게 거부한다")
    void isUniqueConstraintViolation_HibernateNonUnique_ReturnsFalse() {
        DataIntegrityViolationException exception = violation(
                CONSTRAINT,
                ConstraintViolationException.ConstraintKind.OTHER
        );

        assertThat(RoutineConstraintViolationInspector.isUniqueConstraintViolation(
                exception, CONSTRAINT)).isFalse();
    }

    @Test
    @DisplayName("Hibernate 제약 정보가 없는 JDBC 중복 키 예외는 메시지를 보조 수단으로 사용한다")
    void isUniqueConstraintViolation_JdbcMessageFallback_ReturnsTrue() {
        DuplicateKeyException exception = new DuplicateKeyException(
                "Duplicate entry for key 'member_routine." + CONSTRAINT + "'"
        );

        assertThat(RoutineConstraintViolationInspector.isUniqueConstraintViolation(
                exception, CONSTRAINT)).isTrue();
    }

    @Test
    @DisplayName("다른 UNIQUE 제약은 거부한다")
    void isUniqueConstraintViolation_DifferentConstraint_ReturnsFalse() {
        assertThat(RoutineConstraintViolationInspector.isUniqueConstraintViolation(
                violation(
                        "uk_member_routine_schedule_day",
                        ConstraintViolationException.ConstraintKind.UNIQUE
                ),
                CONSTRAINT
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
