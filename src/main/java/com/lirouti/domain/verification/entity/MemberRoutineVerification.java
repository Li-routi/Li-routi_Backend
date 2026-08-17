package com.lirouti.domain.verification.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개인 루틴 인증.
 *
 * <p><b>이 행이 곧 "그날 수행했다"는 기록이다.</b> 개인 루틴에는 날짜별 행이 없어서
 * ({@code member_routine_schedule} 은 요일만 정의한다) 완료를 표현할 다른 자리가 없다.
 * 그룹 루틴과 반대다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_routine_verification",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_routine_verification_routine_date",
                columnNames = {"member_routine_id", "verified_date"}
        )
)
public class MemberRoutineVerification extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_routine_id", nullable = false)
    private MemberRoutine memberRoutine;

    /**
     * 인증 기준일. KST 자정을 경계로 한다.
     *
     * 사진 key 의 날짜 구간과 다를 수 있다 — 그쪽은 업로드일이라 23:59 에 발급받아
     * 00:01 에 인증하면 어긋난다. 업무 판정은 항상 이 컬럼을 본다.
     */
    @Column(name = "verified_date", nullable = false)
    private LocalDate verifiedDate;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    /** 전체 URL이 아니라 S3 오브젝트 key를 담는다. 읽기 URL은 조회 시 조립한다. */
    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(length = 255)
    private String content;

    @Builder
    private MemberRoutineVerification(
            MemberRoutine memberRoutine,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt,
            String imageUrl,
            String content
    ) {
        this.memberRoutine = memberRoutine;
        this.verifiedDate = verifiedDate;
        this.verifiedAt = verifiedAt;
        this.imageUrl = imageUrl;
        this.content = content;
    }
}
