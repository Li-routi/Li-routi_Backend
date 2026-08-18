package com.lirouti.domain.notification.controller.docs;

import com.lirouti.domain.notification.dto.request.NotificationReqDTO;
import com.lirouti.domain.notification.dto.response.NotificationResDTO;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
        name = "알림",
        description = """
                사용자 설정 대상 알림은 수신이 활성화된 경우에만 저장·전송됩니다.
                필수 알림은 설정과 무관하며, 실제 Push 전송에는 올바른 FCM 설정과 토큰이 필요합니다.
                """
)
public interface NotificationControllerDocs {

    /**
     * Android FCM 등록 토큰 저장 API 명세다.
     *
     * @param user 인증 회원 정보
     * @param request Firebase Android SDK가 발급·갱신한 등록 토큰
     * @return 등록 결과(active=true)
     */
    @Operation(
            summary = "FCM 기기 토큰 등록",
            description = """
                    Firebase Android SDK가 발급하거나 갱신한 FCM 등록 토큰을 인증 회원에게 등록합니다.
                    앱 최초 실행, 토큰 갱신(`onNewToken`), 로그인 성공 시점마다 호출해 주세요.

                    ### 동작 방식

                    - token은 전역에서 유일합니다. 같은 토큰으로 다시 요청하면 새 행을 만들지 않고
                      기존 행을 재사용해 활성화합니다.
                    - 같은 물리 기기에서 다른 계정으로 로그인하면, 그 기기의 FCM 토큰은 마지막으로
                      로그인한 회원에게 재귀속됩니다(이전 계정에서는 자동으로 해제됨).
                    - 여러 기기(태블릿+휴대폰 등)를 동시에 등록할 수 있으며, 알림은 활성 상태인
                      모든 기기로 동시에 전송됩니다.
                    - 응답에는 원문 토큰을 다시 노출하지 않습니다.

                    ### 요청 예시

                    ```json
                    { "token": "dQw4w9WgXcQ:APA91bF...(Firebase가 발급한 실제 등록 토큰)" }
                    ```

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `COMMON400_1` | 400 | token이 비었거나 512자를 초과 | 입력값 문제이므로 재시도하지 않고 로그만 남김 |
                    | `AUTH401_1` | 401 | 유효하지 않거나 만료된 인증 토큰 | 재로그인 유도 |
                    | `COMMON404_1` | 404 | 인증 토큰이 가리키는 회원이 DB에 없음(탈퇴 등) | 로그아웃 처리 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "FCM 기기 토큰 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "token 형식 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "인증 토큰이 참조하는 회원을 찾을 수 없음")
    })
    ApiResponse<NotificationResDTO.DeviceRegistration> registerDevice(
            @Parameter(hidden = true) CustomUserDetails user,
            NotificationReqDTO.RegisterDevice request
    );

    /**
     * Android FCM 등록 토큰 비활성화 API 명세다.
     *
     * @param user 인증 회원 정보
     * @param request 비활성화할 현재 기기의 FCM 등록 토큰
     * @return 처리 결과(active=false)
     */
    @Operation(
            summary = "FCM 기기 토큰 해제",
            description = """
                    로그아웃 시 현재 Android 기기의 FCM 등록 토큰을 비활성화합니다.
                    비활성화된 토큰으로는 더 이상 Push가 전송되지 않습니다.

                    ### 동작 방식

                    - 멱등 처리입니다. 이미 비활성 상태이거나, 해당 토큰이 존재하지 않거나,
                      다른 회원 소유의 토큰이어도 항상 200과 `active: false`를 반환합니다
                      (보안상 토큰 존재 여부를 노출하지 않기 위함).
                    - 다시 로그인하면 `POST /api/notifications/devices`로 같은 토큰을 재등록해
                      활성화할 수 있습니다.

                    ### 요청 예시

                    ```json
                    { "token": "dQw4w9WgXcQ:APA91bF...(로그아웃하는 기기의 등록 토큰)" }
                    ```

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `COMMON400_1` | 400 | token이 비었거나 512자를 초과 | 입력값 문제이므로 로그아웃 자체는 별도로 진행 |
                    | `AUTH401_1` | 401 | 유효하지 않거나 만료된 인증 토큰 | 이미 로그아웃 흐름이므로 화면에서는 무시 가능 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "FCM 기기 토큰 해제 성공(멱등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "token 형식 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰")
    })
    ApiResponse<NotificationResDTO.DeviceRegistration> unregisterDevice(
            @Parameter(hidden = true) CustomUserDetails user,
            NotificationReqDTO.UnregisterDevice request
    );

    /**
     * 사용자 알림 설정 조회 API 명세다.
     *
     * @param user 인증 회원 정보
     * @return 여섯 알림 설정의 현재 상태
     */
    @Operation(
            summary = "알림 설정 조회",
            description = """
                    인증 회원의 루틴 마감, 새 인증, 내 인증 반응, 콕콕, 새 채팅, 좋아요 알림
                    수신 여부를 조회합니다. 신규 회원과 기존 회원의 초기값은 모두 true입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "알림 설정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "인증 토큰이 참조하는 회원을 찾을 수 없음")
    })
    ApiResponse<NotificationResDTO.Settings> getSettings(
            @Parameter(hidden = true) CustomUserDetails user
    );

    /**
     * 사용자 알림 설정 부분 변경 API 명세다.
     *
     * @param user 인증 회원 정보
     * @param request 변경할 설정만 담은 요청
     * @return 변경 후 여섯 알림 설정 전체
     */
    @Operation(
            summary = "알림 설정 변경",
            description = """
                    요청에 포함된 알림 설정만 변경하고, 생략된 항목은 기존 값을 유지합니다.
                    빈 JSON 객체도 변경 없이 현재 설정을 반환하므로 멱등하게 호출할 수 있습니다.

                    설정을 끄면 이후 해당 유형의 앱 내 알림 행과 FCM Push가 모두 생성되지 않습니다.
                    이미 생성된 과거 알림은 최근 7일 목록에 그대로 유지됩니다. 챌린지 심사 결과·제한,
                    그룹 가입·루틴 변경 같은 중요 알림은 여섯 설정과 무관하게 항상 제공됩니다.

                    ### 요청 예시

                    ```json
                    { "newChatEnabled": false, "pokeEnabled": true }
                    ```
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "알림 설정 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "인증 토큰이 참조하는 회원을 찾을 수 없음")
    })
    ApiResponse<NotificationResDTO.Settings> updateSettings(
            @Parameter(hidden = true) CustomUserDetails user,
            NotificationReqDTO.UpdateSettings request
    );

    /**
     * 알림센터 목록 조회 API 명세다.
     *
     * @param user 인증 회원 정보
     * @param category 조회할 분류. 생략하면 전체
     * @param cursor 이전 페이지 마지막 알림의 id. 생략하면 첫 페이지
     * @param size 페이지 크기(1~50). 생략하면 20
     * @return 알림 목록과 다음 페이지 커서
     */
    @Operation(
            summary = "알림 목록 조회",
            description = """
                    인증 회원의 최근 7일 알림을 id 역순(최신순) 커서 기반으로 조회합니다.
                    7일이 지난 알림은 매일 새벽 배치가 정리하므로 응답에 나타나지 않습니다.

                    ### 탭·페이지 처리

                    - category를 생략하면 "전체" 탭입니다. 지정하면 해당 분류만 필터링합니다.
                    - 무한 스크롤: 첫 호출은 cursor 없이 보내고, 응답의 nextCursor를 다음 호출의
                      cursor로 그대로 넘기면 됩니다. hasNext가 false거나 nextCursor가 null이면
                      마지막 페이지입니다.
                    - size는 1~50 범위로 자동 보정되며, 생략 시 기본값 20입니다.
                    - 응답의 read는 이 알림을 읽었는지 여부이며, 목록 조회 자체는 읽음 처리를
                      하지 않습니다(별도로 읽음 API를 호출해야 함).

                    ### category 값

                    | category | 의미 |
                    | --- | --- |
                    | `PERSONAL_ROUTINE` | 개인 루틴 알림(수행 알림, 마감 임박, 놓침) |
                    | `CHALLENGE` | 챌린지 알림(좋아요, 새 주기 시작, 신고 누적 제한, 대기 인증 승인·반려) |
                    | `GROUP_ROUTINE` | 그룹 루틴 알림(가입, 인증·좋아요·아쉬워요, 찌르기, 일정 변경, 시작·마감·종료) |
                    | `CHAT` | 그룹 채팅 새 메시지 알림 |

                    ### 요청 예시

                    `GET /api/notifications?category=GROUP_ROUTINE&size=20`
                    (다음 페이지는 `?category=GROUP_ROUTINE&cursor=42&size=20`)

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `COMMON400_1` | 400 | category가 정의되지 않은 값이거나 cursor·size가 숫자가 아님 | 쿼리 파라미터 재확인 |
                    | `AUTH401_1` | 401 | 유효하지 않거나 만료된 인증 토큰 | 재로그인 유도 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "알림 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "category·cursor·size 파라미터 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰")
    })
    ApiResponse<NotificationResDTO.Page> getNotifications(
            @Parameter(hidden = true) CustomUserDetails user,
            @Parameter(description = "조회할 알림 분류. 생략하면 전체", example = "GROUP_ROUTINE")
            NotificationCategory category,
            @Parameter(description = "이전 페이지 마지막 알림 id. 생략하면 첫 페이지", example = "42")
            Long cursor,
            @Parameter(description = "페이지 크기(1~50). 생략하면 20", example = "20")
            Integer size
    );

    /**
     * 알림 단건 읽음 처리 API 명세다.
     *
     * @param user 인증 회원 정보
     * @param notificationId 읽음 처리할 알림 ID
     */
    @Operation(
            summary = "알림 읽음 처리",
            description = """
                    인증 회원 소유의 알림 한 건을 읽음 처리합니다.
                    이미 읽은 알림을 다시 호출해도 최초 읽은 시각을 유지한 채 200으로 응답합니다(멱등).

                    다른 회원의 알림 ID를 넣으면 존재 여부를 노출하지 않기 위해 항상 404로 응답합니다.

                    ### 에러 코드

                    | code | HTTP | 언제 | 화면 처리 |
                    | --- | --- | --- | --- |
                    | `AUTH401_1` | 401 | 유효하지 않거나 만료된 인증 토큰 | 재로그인 유도 |
                    | `COMMON404_1` | 404 | 알림이 없거나 다른 회원 소유 | 목록을 다시 조회해 최신 상태로 갱신 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "알림 읽음 처리 성공(멱등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "알림이 없거나 다른 회원 소유")
    })
    ApiResponse<Void> markRead(
            @Parameter(hidden = true) CustomUserDetails user,
            @Parameter(description = "읽음 처리할 알림 ID", example = "42") Long notificationId
    );

    /**
     * 전체 알림 읽음 처리 API 명세다.
     *
     * @param user 인증 회원 정보
     * @return 실제로 읽음 처리된 알림 수
     */
    @Operation(
            summary = "알림 전체 읽음 처리",
            description = """
                    인증 회원의 읽지 않은 알림을 모두 한 번에 읽음 처리합니다.
                    읽지 않은 알림이 하나도 없으면 updatedCount가 0인 채로 200을 반환합니다(항상 성공).

                    ### 에러 코드

                    | code | HTTP | 언제 |
                    | --- | --- | --- |
                    | `AUTH401_1` | 401 | 유효하지 않거나 만료된 인증 토큰 |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "알림 전체 읽음 처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효하지 않거나 만료된 인증 토큰")
    })
    ApiResponse<NotificationResDTO.ReadAll> markAllRead(
            @Parameter(hidden = true) CustomUserDetails user
    );

    ApiResponse<Void> markClicked(
            @Parameter(hidden = true) CustomUserDetails user,
            @Parameter(description = "클릭된 알림 ID") Long notificationId
    );
}
