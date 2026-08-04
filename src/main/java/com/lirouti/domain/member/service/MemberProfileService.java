package com.lirouti.domain.member.service;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.dto.request.MemberReqDTO;
import com.lirouti.domain.member.dto.response.MemberResDTO;
import com.lirouti.domain.member.service.command.MemberCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * CQRS 예외 서비스 - DB를 직접 다루지 않고 흐름만 조정함.
 */
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MediaService mediaService;
    private final MemberCommandService memberCommandService;

    public MemberResDTO.MemberInfo updateProfile(Long memberId, MemberReqDTO.UpdateProfile request) {
        String profileImageKey = request.profileImageKey();

        if(profileImageKey != null) {
            mediaService.validateMediaKey(profileImageKey, MediaPurpose.PROFILE); // 포맷 검증, IO 없음
            mediaService.validateUploadedBytes(profileImageKey, MediaPurpose.PROFILE); // S3 호출, 트랜잭션 밖
        }

        return memberCommandService.updateProfile(memberId, request);
    }
}
