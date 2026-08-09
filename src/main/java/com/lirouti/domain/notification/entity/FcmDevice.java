package com.lirouti.domain.notification.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Android Firebase SDK가 발급한 회원별 FCM 등록 토큰이다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "fcm_device")
public class FcmDevice extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    @Column(nullable = false, unique = true, length = 512)
    private String token;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private LocalDateTime lastRegisteredAt;
    private LocalDateTime deactivatedAt;

    /** 처음 전달받은 Android 토큰을 활성 상태로 등록한다. */
    public FcmDevice(Member member, String token, LocalDateTime now) {
        this.member = member; this.token = token; this.active = true; this.lastRegisteredAt = now;
    }
    /** 토큰 갱신 또는 다른 계정 로그인 시 현재 회원에게 안전하게 재귀속한다. */
    public void activateFor(Member member, LocalDateTime now) {
        this.member = member; this.active = true; this.lastRegisteredAt = now; this.deactivatedAt = null;
    }
    /** 로그아웃 또는 FCM의 만료 응답을 받은 토큰을 비활성화한다. */
    public void deactivate(LocalDateTime now) { active = false; deactivatedAt = now; }
}
