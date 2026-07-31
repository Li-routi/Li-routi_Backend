package com.lirouti.domain.group.service.command;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 초대코드 unique 제약 위반만 다른 무결성 오류와 구분한다. */
@Component
public class GroupInviteCodeUniqueViolationDetector {
    private static final String INVITE_CODE_UNIQUE_CONSTRAINT =
            "UKmgt3kl7whp0n031hlo8x6jupi";

    public boolean isInviteCodeUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolationException
                    && constraintViolationException.getKind()
                    == ConstraintViolationException.ConstraintKind.UNIQUE) {
                return isInviteCodeConstraint(constraintViolationException.getConstraintName());
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isInviteCodeConstraint(String constraintName) {
        return INVITE_CODE_UNIQUE_CONSTRAINT.equals(constraintName)
                || constraintName != null
                && constraintName.endsWith("." + INVITE_CODE_UNIQUE_CONSTRAINT);
    }
}
