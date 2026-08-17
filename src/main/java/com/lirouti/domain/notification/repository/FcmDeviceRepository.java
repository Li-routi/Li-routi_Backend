package com.lirouti.domain.notification.repository;

import com.lirouti.domain.notification.entity.FcmDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Android FCM 토큰의 전역 유일성과 회원별 활성 목록을 제공한다. */
public interface FcmDeviceRepository extends JpaRepository<FcmDevice, Long> {
    Optional<FcmDevice> findByToken(String token);
    List<FcmDevice> findAllByMemberIdAndActiveTrue(Long memberId);

    /**
     * 전역 유일 토큰을 현재 회원의 활성 기기로 원자적으로 등록하거나 재귀속한다.
     *
     * <p>조회 후 삽입하는 방식은 동일 토큰의 동시 등록 사이에 UNIQUE 충돌이 생길 수 있다.
     * MySQL upsert 한 문장으로 신규 등록과 기존 행 재활성화를 함께 처리한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO fcm_device
              (member_id, token, active, last_registered_at, deactivated_at, created_at, updated_at)
            VALUES (:memberId, :token, b'1', :now, NULL, :now, :now)
            ON DUPLICATE KEY UPDATE
              member_id = VALUES(member_id),
              active = b'1',
              last_registered_at = VALUES(last_registered_at),
              deactivated_at = NULL,
              updated_at = VALUES(updated_at)
            """, nativeQuery = true)
    void upsertActive(
            @Param("memberId") Long memberId,
            @Param("token") String token,
            @Param("now") LocalDateTime now
    );

    /**
     * 토큰이 아직 요청 회원 소유일 때만 비활성화한다.
     * 다른 계정으로 재귀속된 뒤 도착한 이전 계정의 로그아웃 요청은 아무 행도 바꾸지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update FcmDevice device
            set device.active = false,
                device.deactivatedAt = :now
            where device.token = :token
              and device.member.id = :memberId
              and device.active = true
            """)
    int deactivateOwnedToken(
            @Param("memberId") Long memberId,
            @Param("token") String token,
            @Param("now") LocalDateTime now
    );
}
