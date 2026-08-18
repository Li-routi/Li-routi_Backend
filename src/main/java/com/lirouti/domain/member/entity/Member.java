package com.lirouti.domain.member.entity;

import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "member",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_social_provider_social_id",
            columnNames = {"social_provider", "social_id"}
        )
    }
)
public class Member extends BaseEntity {
    private static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false)
    private SocialProvider socialProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "social_id", nullable = false)
    private String socialId;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted; // 참 거짓 값은 초기값이 존재해야 하므로, 원시타입으로 정의

    @Column(nullable = false)
    private Boolean isActive; // 회원 탈퇴 여부

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "profile_image_key")
    private String profileImageKey; // null = 기본 회색 아바타 (기본 아바타는 프론트에서 처리)

    @Column(name = "routine_deadline_notification_enabled", nullable = false)
    private boolean routineDeadlineNotificationEnabled;

    @Column(name = "new_verification_notification_enabled", nullable = false)
    private boolean newVerificationNotificationEnabled;

    @Column(name = "verification_reaction_notification_enabled", nullable = false)
    private boolean verificationReactionNotificationEnabled;

    @Column(name = "poke_notification_enabled", nullable = false)
    private boolean pokeNotificationEnabled;

    @Column(name = "new_chat_notification_enabled", nullable = false)
    private boolean newChatNotificationEnabled;

    @Column(name = "like_notification_enabled", nullable = false)
    private boolean likeNotificationEnabled;

    @Column(name = "representative_achievement_id")
    private Long representativeAchievementId;

    @Builder
    private Member(
            String email,
            String nickname,
            SocialProvider socialProvider,
            Role role,
            String socialId
    ) {
        this.email = email;
        this.nickname = nickname;
        this.socialProvider = socialProvider;
        this.role = role;
        this.socialId = socialId;
        this.onboardingCompleted = false;
        this.isActive = true;
        this.deletedAt = null;
        this.routineDeadlineNotificationEnabled = true;
        this.newVerificationNotificationEnabled = true;
        this.verificationReactionNotificationEnabled = true;
        this.pokeNotificationEnabled = true;
        this.newChatNotificationEnabled = true;
        this.likeNotificationEnabled = true;
    }

    // 회원이 서비스에 접근할 수 있는 활성 상태인지 확인
    public boolean isActiveMember() {
        return Boolean.TRUE.equals(isActive) && deletedAt == null;
    }

    // 회원 탈퇴 처리
    public void withdraw(
            String anonymizedEmail,
            String anonymizedSocialId,
            LocalDateTime withdrawnAt
    ) {
        this.email = anonymizedEmail;
        this.nickname = WITHDRAWN_NICKNAME;
        this.socialId = anonymizedSocialId;
        this.role = Role.ROLE_USER;
        this.onboardingCompleted = false;
        this.isActive = false;
        this.deletedAt = withdrawnAt;
    }

    // 프로필 내 닉네임 & 이미지 수정 및 온보딩 완료 처리
    public void updateProfile(String nickname, String profileImageKey) {
        this.nickname = nickname;
        this.profileImageKey = profileImageKey;
        this.onboardingCompleted = true;
    }

    /**
     * 전달된 알림 설정만 변경하고, 생략된 설정은 기존 값을 유지한다.
     *
     * @param routineDeadlineEnabled 루틴 마감·리마인드 알림 설정
     * @param newVerificationEnabled 그룹원의 새 인증 알림 설정
     * @param verificationReactionEnabled 내 그룹 인증 반응 알림 설정
     * @param pokeEnabled 콕콕 알림 설정
     * @param newChatEnabled 새 그룹 채팅 알림 설정
     * @param likeEnabled 챌린지 인증 좋아요 알림 설정
     */
    public void updateNotificationSettings(
            Boolean routineDeadlineEnabled,
            Boolean newVerificationEnabled,
            Boolean verificationReactionEnabled,
            Boolean pokeEnabled,
            Boolean newChatEnabled,
            Boolean likeEnabled
    ) {
        if (routineDeadlineEnabled != null) {
            this.routineDeadlineNotificationEnabled = routineDeadlineEnabled;
        }
        if (newVerificationEnabled != null) {
            this.newVerificationNotificationEnabled = newVerificationEnabled;
        }
        if (verificationReactionEnabled != null) {
            this.verificationReactionNotificationEnabled = verificationReactionEnabled;
        }
        if (pokeEnabled != null) {
            this.pokeNotificationEnabled = pokeEnabled;
        }
        if (newChatEnabled != null) {
            this.newChatNotificationEnabled = newChatEnabled;
        }
        if (likeEnabled != null) {
            this.likeNotificationEnabled = likeEnabled;
        }
    }

    /**
     * 홈 화면·그룹 프로필에 노출할 대표 업적을 설정한다. 배지 이미지가 있는 CLAIMED
     * 업적인지 검증은 호출부(RepresentativeAchievementCommandService)의 책임이다 -
     * 엔티티는 단순히 값을 갖는다.
     */
    public void selectRepresentativeAchievement(Long achievementId) {
        this.representativeAchievementId = achievementId;
    }

    public void clearRepresentativeAchievement() {
        this.representativeAchievementId = null;
    }
}
