package com.lirouti.domain.chat.entity;

import com.lirouti.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스가 관리하는 그룹 채팅 이모티콘 자산의 메타데이터다.
 *
 * 실제 파일은 S3에 두고, DB에는 조회 URL이 아니라 object key를 저장한다.
 * 비활성화된 자산도 기존 메시지가 참조할 수 있으므로 행을 삭제하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_emoticon")
public class ChatEmoticon extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String code;

    @Column(name = "asset_key", nullable = false, length = 500)
    private String assetKey;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(nullable = false)
    private Boolean animated;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    /**
     * 서비스 자산의 메타데이터를 생성한다. 기본 정렬 순서는 0이고 신규 자산은 활성 상태다.
     */
    @Builder
    private ChatEmoticon(
            String code,
            String assetKey,
            String contentType,
            Boolean animated,
            Boolean active,
            Integer displayOrder
    ) {
        this.code = code;
        this.assetKey = assetKey;
        this.contentType = contentType;
        this.animated = animated != null && animated;
        this.active = active == null || active;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
    }

    /** 신규 메시지 선택 대상에서 제외하되 기존 메시지의 자산 참조는 보존한다. */
    public void deactivate() {
        this.active = false;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
