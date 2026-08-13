package com.lirouti.domain.character.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 회원이 해금한 캐릭터.
 *
 * <p><b>행이 있으면 해금된 것이고, 없으면 알이다.</b> 상태 컬럼을 따로 두지 않는다 — 전이가
 * 한 방향뿐이라 행의 존재가 곧 상태다.
 *
 * <p><b>유니크가 해금의 멱등을 보장한다.</b> 판정이 두 번 돌아도 두 번째 INSERT 가 막히고,
 * 성공한 요청만 팝업을 발행한다. 제약 위반은 "졌다" 는 뜻이라 조용히 끝낸다 — 오류로 올리지
 * 않는다. 이 프로젝트가 인증 중복을 다루는 방식과 같다.
 *
 * <p>그 유니크는 {@link MemberSelectedCharacter} 의 복합 외래 키가 참조할 대상이기도 하다.
 */
@Entity
@Getter
@Table(
        name = "member_character",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_character",
                columnNames = {"member_id", "character_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCharacter extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private AvatarCharacter avatarCharacter;

    /** 해금된 날(KST). */
    @Column(name = "unlocked_date", nullable = false)
    private LocalDate unlockedDate;

    @Builder
    private MemberCharacter(Member member, AvatarCharacter avatarCharacter,
                            LocalDate unlockedDate) {
        this.member = member;
        this.avatarCharacter = avatarCharacter;
        this.unlockedDate = unlockedDate;
    }
}
