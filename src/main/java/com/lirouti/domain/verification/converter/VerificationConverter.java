package com.lirouti.domain.verification.converter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;

/**
 * 인증 엔티티를 응답으로 옮긴다.
 *
 * <p><b>사진 주소는 여기서 만들지 않고 받아 온다.</b> 비공개 인증의 주소는 서명이라
 * 자격 증명과 설정이 필요한데, Converter 는 외부 처리 결과를 인자로만 받는다
 * (converter_convention). 서명은 조회 서비스가 미리 만들어 Map 으로 넘긴다.
 */
public final class VerificationConverter {

    private VerificationConverter() {
    }

    public static VerificationResDTO.GroupRoutineFeed toGroupRoutineFeed(
            List<GroupRoutineVerification> verifications,
            Map<Long, String> imageUrls,
            Map<Long, Long> likeCounts,
            Set<Long> likedVerificationIds,
            Long nextCursor,
            boolean hasNext
    ) {
        return VerificationResDTO.GroupRoutineFeed.builder()
                .verifications(verifications.stream()
                        .map(verification -> toGroupRoutineItem(
                                verification,
                                imageUrls.get(verification.getId()),
                                likeCounts.getOrDefault(verification.getId(), 0L),
                                likedVerificationIds.contains(verification.getId())))
                        .toList())
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }

    /**
     * 작성자는 할당을 거쳐 읽는다. 조회 쿼리가 회원까지 fetch join 해 두므로 여기서 추가 쿼리가
     * 나가지 않는다 — 그 join 을 빼면 이 줄이 페이지 크기만큼 회원 조회를 일으킨다.
     */
    public static VerificationResDTO.GroupRoutineItem toGroupRoutineItem(
            GroupRoutineVerification verification,
            String imageUrl,
            long likeCount,
            boolean liked
    ) {
        Member author = verification.getAssignment().getMember();
        return VerificationResDTO.GroupRoutineItem.builder()
                .verificationId(verification.getId())
                .assignmentId(verification.getAssignment().getId())
                .memberId(author.getId())
                .nickname(author.getNickname())
                .imageUrl(imageUrl)
                .content(verification.getContent())
                .verifiedAt(verification.getVerifiedAt())
                .likeCount(likeCount)
                .liked(liked)
                .build();
    }

    public static VerificationResDTO.GroupRoutineLike toGroupRoutineLike(
            Long verificationId,
            long likeCount,
            boolean liked
    ) {
        return new VerificationResDTO.GroupRoutineLike(verificationId, likeCount, liked);
    }
}
