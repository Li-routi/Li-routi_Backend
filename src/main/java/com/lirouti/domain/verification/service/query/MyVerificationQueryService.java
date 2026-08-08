package com.lirouti.domain.verification.service.query;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.verification.converter.MyVerificationConverter;
import com.lirouti.domain.verification.dto.response.MyVerificationResDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.MemberRoutineVerification;
import com.lirouti.domain.verification.repository.ChallengeVerificationRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MyVerificationQueryService {
    private final ChallengeVerificationRepository challengeVerificationRepository;
    private final MemberRoutineVerificationRepository memberRoutineVerificationRepository;
    private final GroupRoutineVerificationRepository groupRoutineVerificationRepository;
    private final MediaService mediaService;

    @Transactional(readOnly = true)
    public MyVerificationResDTO.DailyFeed getMyVerifications(
            Long memberId,
            LocalDate date,
            ReviewStatus statusFilter
    ) {
        List<ChallengeVerification> challengeVerifications =
                challengeVerificationRepository.findByMemberAndDate(memberId, date, statusFilter);
        List<MemberRoutineVerification> memberRoutineVerifications =
                memberRoutineVerificationRepository.findByMemberAndDate(memberId, date);
        List<GroupRoutineVerification> groupRoutineVerifications =
                groupRoutineVerificationRepository.findByMemberAndDate(memberId, date);

        List<MyVerificationResDTO.Item> items = new ArrayList<>();

        for (ChallengeVerification v : challengeVerifications) {
            // 보류 건에 resolveViewUrl 을 쓰면 안 된다. 챌린지 인증은 publicRead = true 라
            // 공개 주소를 조립해 돌려주는데, 보류 사진은 아직 대기 prefix 에 있어 403 이다.
            // 이 조회는 memberId 로 좁혀 본인 것만 담으므로 여기서 서명을 붙여도 된다.
            String imageUrl = v.isPending()
                    ? mediaService.presignedViewUrl(v.getImageUrl())
                    : mediaService.resolveViewUrl(v.getImageUrl(), MediaPurpose.CHALLENGE_VERIFICATION);
            items.add(MyVerificationConverter.ChallengeToItem(v, imageUrl));
        }
        for (MemberRoutineVerification v : memberRoutineVerifications) {
            String imageUrl = mediaService.resolveViewUrl(v.getImageUrl(), MediaPurpose.MEMBER_ROUTINE_VERIFICATION);
            items.add(MyVerificationConverter.RoutineToItem(v, imageUrl));
        }
        for (GroupRoutineVerification v : groupRoutineVerifications) {
            String imageUrl = mediaService.resolveViewUrl(v.getImageUrl(), MediaPurpose.GROUP_ROUTINE_VERIFICATION);
            items.add(MyVerificationConverter.GroupToItem(v, imageUrl));
        }

        return MyVerificationConverter.toDailyFeed(date, items);
    }
}
