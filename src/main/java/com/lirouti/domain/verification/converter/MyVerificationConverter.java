package com.lirouti.domain.verification.converter;

import com.lirouti.domain.verification.dto.response.MyVerificationResDTO;
import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.MemberRoutineVerification;
import com.lirouti.domain.verification.enums.VerificationSourceType;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public class MyVerificationConverter {
    private MyVerificationConverter() {}

    public static MyVerificationResDTO.Item ChallengeToItem(ChallengeVerification verification, String imageUrl) {
        return MyVerificationResDTO.Item.builder()
                .verificationId(verification.getId())
                .sourceType(VerificationSourceType.CHALLENGE)
                .categoryName("챌린지")
                .title(verification.getMemberChallenge().getChallenge().getName())
                .content(verification.getContent())
                .imageUrl(imageUrl)
                .verifiedAt(verification.getVerifiedAt())
                .reviewStatus(verification.getReviewStatus())
                .build();
    }

    public static MyVerificationResDTO.Item RoutineToItem(MemberRoutineVerification verification, String imageUrl) {
        return MyVerificationResDTO.Item.builder()
                .verificationId(verification.getId())
                .sourceType(VerificationSourceType.MEMBER_ROUTINE)
                .categoryName(verification.getMemberRoutine().getCategory().getName())
                .title(verification.getMemberRoutine().getName())
                .content(verification.getContent())
                .imageUrl(imageUrl)
                .verifiedAt(verification.getVerifiedAt())
                .build();
    }

    public static MyVerificationResDTO.Item GroupToItem(GroupRoutineVerification verification, String imageUrl) {
        return MyVerificationResDTO.Item.builder()
                .verificationId(verification.getId())
                .sourceType(VerificationSourceType.GROUP_ROUTINE)
                .categoryName("그룹 루틴")
                .title(verification.getAssignment().getGroupRoutine().getTitle())
                .content(verification.getContent())
                .imageUrl(imageUrl)
                .verifiedAt(verification.getVerifiedAt())
                .build();
    }

    // 세 도메인에서 모은 항목을 인증 시각 최신순으로 합쳐 하루치 응답으로 감싼다.
    public static MyVerificationResDTO.DailyFeed toDailyFeed(LocalDate date, List<MyVerificationResDTO.Item> items) {
        List<MyVerificationResDTO.Item> sorted = items.stream()
                .sorted(Comparator.comparing(MyVerificationResDTO.Item::verifiedAt).reversed())
                .toList();

        return MyVerificationResDTO.DailyFeed.builder()
                .date(date)
                .verifications(sorted)
                .build();
    }
}
