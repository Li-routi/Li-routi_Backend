package com.lirouti.domain.verification.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 그룹 루틴 인증. 할당 1건에 인증 1건이다.
 *
 * <p><b>완료 여부는 이 행이 아니라 할당의 status 가 들고 있다.</b> 즉 이 행은 완료에 붙는
 * 증빙이지 완료 그 자체가 아니다. 개인 루틴 인증과 반대다.
 *
 * <p>회원·날짜를 다시 들고 있지 않다. 할당이 이미 {@code (루틴, 회원, 날짜)} 로 유니크하므로
 * 할당을 참조하는 것만으로 "하루에 한 번"이 따라온다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "group_routine_verification",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_routine_verification_assignment",
                columnNames = "group_routine_assignment_id"
        )
)
public class GroupRoutineVerification extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_routine_assignment_id", nullable = false)
    private GroupRoutineAssignment assignment;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    /** 전체 URL이 아니라 S3 오브젝트 key를 담는다. 읽기 URL은 조회 시 조립한다. */
    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(length = 255)
    private String content;

    /** 인증이 그룹 삭제 등으로 제거될 때 좋아요 FK도 함께 정리한다. */
    @OneToMany(mappedBy = "groupRoutineVerification", cascade = CascadeType.REMOVE)
    private List<GroupRoutineVerificationLike> likes = new ArrayList<>();

    @Builder
    private GroupRoutineVerification(
            GroupRoutineAssignment assignment,
            LocalDateTime verifiedAt,
            String imageUrl,
            String content
    ) {
        this.assignment = assignment;
        this.verifiedAt = verifiedAt;
        this.imageUrl = imageUrl;
        this.content = content;
    }

    /** 기존 피드·읽음 커서 참조를 유지한 채 인증 내용을 교체한다. */
    public void reverify(String imageUrl, String content, LocalDateTime verifiedAt) {
        this.imageUrl = imageUrl;
        this.content = content;
        this.verifiedAt = verifiedAt;
    }
}
