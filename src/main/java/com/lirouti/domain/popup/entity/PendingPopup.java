package com.lirouti.domain.popup.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 앱을 켰을 때 반드시 한 번 보여줘야 하는 팝업.
 *
 * <p><b>알림함과 다른 것이다.</b> 알림함은 목록이고 나중에 봐도 되지만, 이것은 즉시 1회이고
 * 사용자가 봤다는 확인이 있어야 사라진다. 앱 밖으로 알리는 일(푸시)은 알림 도메인이 하고,
 * 발행하는 쪽이 둘을 함께 부른다.
 *
 * <p><b>이 모듈은 도메인을 모른다.</b> 캐릭터·업적을 참조로 들고 있으면 조회할 때 그것들을
 * 전부 알아야 하고, 소비자가 늘 때마다 모듈을 고쳐야 한다. 그래서 발행하는 쪽이 보여줄
 * 내용을 채워 넣고 모듈은 그대로 돌려준다.
 */
@Entity
@Getter
@Table(
        name = "pending_popup",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pending_popup_member_dedup",
                columnNames = {"member_id", "dedup_key"}
        ),
        indexes = @Index(
                name = "idx_pending_popup_member_acked",
                columnList = "member_id, acked_at, created_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingPopup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /**
     * 소비자가 정하는 종류. 자바 enum 으로 두지 않는다 — 소비자가 늘 때 이 모듈이 바뀌지
     * 않아야 한다.
     */
    @Column(name = "popup_type", nullable = false, length = 30)
    private String popupType;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "body", nullable = false, length = 255)
    private String body;

    /** 절대 URL 이 아니라 S3 key. 조회에서 조립한다. 없으면 {@code null}. */
    @Column(name = "image_key", length = 512)
    private String imageKey;

    /** 눌렀을 때 어디로 보낼지. 모듈은 해석하지 않고 그대로 내려준다. */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    /**
     * 같은 사건이면 몇 번을 다시 계산해도 <b>같은 문자열</b>이 나와야 한다.
     *
     * <p>요청 id 나 UUID 를 넣으면 재시도마다 달라져 유니크가 아무것도 막지 못한다.
     * 예: {@code character-unlocked:7}
     */
    @Column(name = "dedup_key", nullable = false, length = 100)
    private String dedupKey;

    /** {@code null} 이면 아직 안 보여줬다. */
    @Column(name = "acked_at")
    private LocalDateTime ackedAt;

    @Builder
    private PendingPopup(Member member, String popupType, String title, String body,
                         String imageKey, String referenceType, Long referenceId,
                         String dedupKey) {
        this.member = member;
        this.popupType = popupType;
        this.title = title;
        this.body = body;
        this.imageKey = imageKey;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.dedupKey = dedupKey;
    }

    /**
     * 확인 처리. <b>이미 확인한 것을 다시 확인해도 그대로 둔다</b> — 처음 본 시각이 뒤로
     * 밀리면 "언제 보여줬는가" 가 흔들린다. 재시도가 정상 경로라 오류로 만들지 않는다.
     */
    public void ack(LocalDateTime now) {
        if (this.ackedAt == null) {
            this.ackedAt = now;
        }
    }

    public boolean isAcked() {
        return this.ackedAt != null;
    }
}
