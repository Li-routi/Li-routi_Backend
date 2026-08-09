package com.lirouti.domain.notification.repository;

import com.lirouti.domain.notification.entity.FcmDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Android FCM 토큰의 전역 유일성과 회원별 활성 목록을 제공한다. */
public interface FcmDeviceRepository extends JpaRepository<FcmDevice, Long> {
    Optional<FcmDevice> findByToken(String token);
    Optional<FcmDevice> findByTokenAndMemberId(String token, Long memberId);
    List<FcmDevice> findAllByMemberIdAndActiveTrue(Long memberId);
}
