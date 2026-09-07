package com.pawtrail.user.domain.exception;

import com.pawtrail.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 이 서비스의 도메인 에러 코드입니다.
 *
 * 공통 코드는 CommonErrorCode 에 있고 도메인 개념은 여기에 둡니다.
 * 공통에 두면 코드 하나를 더할 때마다 공통 모듈 재배포와 전 서비스 버전업이 필요해집니다.
 *
 * getCode 는 반드시 name 을 그대로 반환합니다.
 * 상수 이름이 곧 응답의 code 값이자 API 계약인데, 규칙을 어겨도 컴파일러가 잡지 못합니다.
 *
 * 메시지는 고정 문자열입니다. 동적인 값이 필요하면 응답 data 에 담습니다.
 *
 * 이 파일은 방문 기록에서 처음 생겼습니다.
 * 그전까지는 CommonErrorCode 여섯 개로 충분했습니다.
 * 프로필도 즐겨찾기도 실패가 "값이 잘못됐다" 나 "없다" 로 표현되는 것뿐이었고,
 * 우리가 이름을 붙여야 하는 실패가 처음 나온 자리가 여기입니다.
 */
public enum UserErrorCode implements ErrorCode {

    // ── 방문 기록 ─────────────────────────────────────────────
    // 판정 서비스가 응답하지 않아 방문 기록을 만들지 못함
    //
    // * 왜 요청 자체를 실패시키는가
    //   verdict_at_visit 은 방문 시점의 판정을 박아 두는 값임
    //   조건이 바뀌어도 안 바뀌는 것이 존재 이유라 한 번 잘못 들어가면
    //   영구히 잘못되고 고치는 API 도 없음
    //   [다녀왔어요] 는 며칠 전 일정을 지금 기록하는 동작이라
    //   몇 분 뒤에 다시 눌러도 사용자가 잃는 것이 없음
    //
    // * UNKNOWN 으로 채우지 않는 이유
    //   그 값은 이미 "펫이 0마리라 판단할 조건 정보가 없음" 이라는 뜻임
    //   서버 장애를 거기에 섞으면 나중에 그 기록을 보고 어느 쪽인지 알 방법이 없음
    //
    // * 즐겨찾기와 정반대임
    //   거기는 판정을 못 받아도 배지만 비우고 목록을 내려보냄
    //   "내가 담아둔 곳 목록" 이라는 화면의 목적이 배지 없이도 이뤄지기 때문임
    //   실패 처리를 공통 모듈에 두지 않고 부르는 쪽이 정하게 한 이유가 이것임
    //
    // * CommonErrorCode.EXTERNAL_API_ERROR 를 쓰지 않는 이유
    //   같은 502 이지만 즐겨찾기 목록의 실패와 코드가 같아짐
    //   프론트가 "목록을 못 불러왔어요" 와 "판정을 못 받아 기록하지 못했어요" 를
    //   가려서 안내해야 하는데 코드가 같으면 경로로만 판단하게 됨
    //
    // * 이름에 API 를 넣지 않은 이유
    //   일정도 판정을 부르므로 그때 같은 코드를 그대로 씀
    VERDICT_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "판정을 불러오지 못해 기록하지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    UserErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    @Override
    public String getCode() {
        return this.name();
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
