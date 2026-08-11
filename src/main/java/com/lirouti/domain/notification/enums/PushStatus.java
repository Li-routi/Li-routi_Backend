package com.lirouti.domain.notification.enums;

/** 앱 내 알림과 연결된 FCM 배송 처리 상태다. */
public enum PushStatus {
    /** 아직 배송 작업이 선점하지 않은 상태다. */
    PENDING,
    /** 한 배송 작업이 원자적으로 선점해 FCM 호출을 수행 중인 상태다. */
    SENDING,
    /** 하나 이상의 활성 기기에 전송된 상태다. */
    SENT,
    /** 활성 기기는 있었지만 모든 FCM 전송이 실패한 상태다. */
    FAILED,
    /** 활성 기기가 없어 Push를 생략한 상태다. */
    SKIPPED
}
