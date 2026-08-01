package com.lirouti.domain.group.service.command;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/** 초대코드 unique 제약 위반만 다른 무결성 오류와 구분한다. */
@Component
public class GroupInviteCodeUniqueViolationDetector {
    public boolean isInviteCodeUniqueViolation(DataIntegrityViolationException exception) {
        return GroupConstraintViolationInspector.isUniqueConstraintViolation(
                exception,
                GroupDatabaseConstraints.INVITE_CODE
        );
    }
}
