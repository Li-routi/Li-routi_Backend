package com.lirouti.domain.group.service.query;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.service.GroupValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupInviteCodeQueryService {
    private final GroupValidationService groupValidationService;

    // 초대코드 조회
    @Transactional(readOnly = true)
    public GroupResDTO.InviteCode getInviteCode(Long groupId, Long memberId) {
        return GroupConverter.toInviteCodeResult(
                groupValidationService.validateGroupOwner(groupId, memberId).getGroup()
        );
    }
}
