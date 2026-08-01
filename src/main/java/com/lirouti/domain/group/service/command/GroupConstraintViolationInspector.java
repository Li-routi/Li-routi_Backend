package com.lirouti.domain.group.service.command;

import org.hibernate.exception.ConstraintViolationException;

/** 예외 원인 체인에서 지정한 DB unique 제약 위반을 찾는다. */
final class GroupConstraintViolationInspector {
    private GroupConstraintViolationInspector() {
    }

    static boolean isUniqueConstraintViolation(
            Throwable throwable,
            String expectedConstraintName
    ) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && constraintViolation.getKind()
                    == ConstraintViolationException.ConstraintKind.UNIQUE
                    && matchesConstraintName(
                            constraintViolation.getConstraintName(),
                            expectedConstraintName
                    )) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean matchesConstraintName(String actual, String expected) {
        return expected.equals(actual)
                || actual != null && actual.endsWith("." + expected);
    }
}
