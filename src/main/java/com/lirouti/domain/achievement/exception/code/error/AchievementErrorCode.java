package com.lirouti.domain.achievement.exception.code.error;

// WalletErrorCode 와 동일한 인터페이스를 구현한다고 가정. 실제 시그니처 확인 후 조정 필요.
public enum AchievementErrorCode {
    NOT_FOUND("ACHIEVEMENT4001", "존재하지 않는 업적입니다."),
    NOT_ACHIEVED("ACHIEVEMENT4002", "아직 달성하지 않은 업적입니다."),
    ALREADY_CLAIMED("ACHIEVEMENT4003", "이미 보상을 수령한 업적입니다.");

    private final String code;
    private final String message;

    AchievementErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
