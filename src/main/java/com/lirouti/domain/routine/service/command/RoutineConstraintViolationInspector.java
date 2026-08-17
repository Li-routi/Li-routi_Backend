package com.lirouti.domain.routine.service.command;

import org.hibernate.exception.ConstraintViolationException;

/** 예외 원인 체인에서 지정한 DB UNIQUE 제약 위반을 판별한다. */
final class RoutineConstraintViolationInspector {
    private RoutineConstraintViolationInspector() {
    }

    /**
     * Hibernate가 제공하는 제약 종류와 이름을 우선 사용하고, 구조화된 Hibernate 예외가
     * 없는 JDBC 번역 경로에서만 예외 메시지를 보조 수단으로 사용한다.
     *
     * @param throwable Spring이 번역한 데이터 접근 예외
     * @param expectedConstraintName 기대하는 UNIQUE 제약 이름
     * @return 해당 UNIQUE 제약 위반이면 {@code true}
     */
    static boolean isUniqueConstraintViolation(
            Throwable throwable,
            String expectedConstraintName
    ) {
        boolean foundHibernateConstraint = false;
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                foundHibernateConstraint = true;
                if (constraintViolation.getKind()
                        == ConstraintViolationException.ConstraintKind.UNIQUE
                        && matchesConstraintName(
                                constraintViolation.getConstraintName(),
                                expectedConstraintName
                        )) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
        }

        return !foundHibernateConstraint
                && containsConstraintName(throwable, expectedConstraintName);
    }

    private static boolean matchesConstraintName(String actual, String expected) {
        return expected.equals(actual)
                || actual != null && (actual.endsWith("." + expected)
                || actual.contains("`" + expected + "`")
                || actual.contains("'" + expected + "'"));
    }

    private static boolean containsConstraintName(Throwable throwable, String expected) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains(expected)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
