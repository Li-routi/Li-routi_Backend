package com.lirouti.domain.routine.enums;

/**
 * 사용자가 추가한 루틴 카테고리에 붙이는 색상이다.
 * 디자인의 색상 칩 7개와 1:1로 대응하며, "없음"은 별도 상수가 아니라 {@code null}로 표현한다.
 *
 * <p>실제 색상 값(HEX)은 여기 두지 않는다. 같은 팔레트라도 라이트·다크 테마와 플랫폼마다
 * 렌더링 값이 다르고, 색을 조정할 때 서버 재배포가 필요해지기 때문이다.
 * 서버는 "어떤 칩을 골랐는가"만 저장하고 실제 색은 클라이언트가 결정한다.
 *
 * <p>앱이 제공하는 고정 카테고리는 색을 갖지 않는다({@code null}).
 */
public enum RoutineCategoryColor {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE,
    MAGENTA,
    BLACK
}
