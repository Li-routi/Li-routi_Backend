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

    // 프로필 내 닉네임 수정 및 온보딩 완료 처리
    public void updateProfile(String nickname) {
        this.nickname = nickname;
        this.onboardingCompleted = true;
    }
}
