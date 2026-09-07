package com.pawtrail.user.presentation.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pawtrail.user.application.dto.input.ItineraryUpdateInput;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 담아 둔 일정을 고치는 요청입니다.
 *
 * 사용자가 고칠 수 있는 것은 이 셋뿐입니다.
 * placeId 는 컬럼이 updatable = false 이고, 장소를 바꾸는 것은 수정이 아니라
 * 다른 일정을 담는 것입니다.
 * visitOrder 는 서버가 채우는 값이라 받지 않습니다.
 *
 * 이 클래스는 record 가 아닙니다.
 *
 * PATCH 는 "보낸 것만 바꾼다" 가 계약이라
 * 필드를 아예 안 보낸 것과 null 을 보낸 것을 갈라야 합니다.
 * record 로 받으면 둘 다 null 이 되어 구분할 수 없고,
 * 그러면 메모만 고치려고 보낸 요청이 동반 동물까지 떼어 냅니다.
 *
 * Jackson 은 JSON 에 그 키가 있을 때만 세터를 부릅니다.
 * 그래서 세터 안에서 플래그를 세우면 별도 라이브러리 없이 세 상태가 갈립니다.
 * 프로필 수정이 같은 구조를 쓰고 있습니다.
 *
 * 세 상태가 이렇게 갈립니다.
 *   키가 없음           provided 가 false        →  그대로 둠
 *   "필드": null       provided 가 true, 값 null →  visitAt 은 400, 나머지는 비움
 *   "필드": 값          provided 가 true, 값 있음 →  바꿈
 *
 * 날짜를 바꾸는 것은 여기서 막지 않습니다.
 * 기존 값과 견주어야 알 수 있는데 요청 객체는 그 값을 모릅니다.
 * 서비스가 원래 일시와 같은 날인지 보고 아니면 400 을 냅니다.
 */
@Getter
@NoArgsConstructor
public class ItineraryUpdateRequest {

    // 방문 예정 일시임
    //
    // 화면의 수정 폼에는 날짜 칸이 없고 시각만 고침
    // 그래도 visit_at 이 한 컬럼이라 요청은 일시를 통째로 보냄
    // "시각만 바꾼다" 를 요청 형태로 표현할 방법이 없음
    private LocalDateTime visitAt;

    private boolean visitAtProvided;

    // 동반 예정 동물임
    // null 을 보내면 뗌. 반려동물을 떠나보냈거나 혼자 가기로 한 경우임
    private UUID petId;

    private boolean petIdProvided;

    @Size(max = 200, message = "메모는 200자까지 쓸 수 있습니다")
    private String memo;

    private boolean memoProvided;

    @JsonProperty("visitAt")
    public void setVisitAt(LocalDateTime visitAt) {
        this.visitAt = visitAt;
        this.visitAtProvided = true;
    }

    @JsonProperty("petId")
    public void setPetId(UUID petId) {
        this.petId = petId;
        this.petIdProvided = true;
    }

    @JsonProperty("memo")
    public void setMemo(String memo) {
        this.memo = memo;
        this.memoProvided = true;
    }

    /**
     * 방문 예정 일시를 지우려는 요청을 막습니다.
     *
     * 컬럼이 NOT NULL 이라 애초에 지울 수 없고,
     * 일정에서 그 값을 빼면 목록에 실릴 자리도 정렬 기준도 사라집니다.
     * 화면에 "시각 지우기" 도 없으므로 어떤 정상 경로로도 오지 않는 요청입니다.
     *
     * 시각을 정하지 않은 상태로 되돌리려면 그 날짜의 00:00 을 보내면 됩니다.
     * 그것이 이 서비스에서 "시각 미정" 을 뜻하는 값입니다.
     *
     * 동반 동물과 메모는 대칭이 아닙니다.
     * 둘 다 null 이 유효한 상태라 지울 수 있습니다.
     */
    @AssertTrue(message = "방문 예정 일시는 지울 수 없습니다")
    public boolean isVisitAtNotCleared() {
        return !visitAtProvided || visitAt != null;
    }

    /**
     * 서비스가 받는 형태로 바꿉니다.
     *
     * visitAt 은 플래그를 넘기지 않습니다.
     * 명시적 null 이 위 검증에서 이미 막히므로 null 은 "안 보냈다" 하나만 뜻합니다.
     *
     * petId 와 memo 는 플래그가 필요합니다.
     * null 이 "비운다" 라는 뜻으로 살아 있기 때문입니다.
     *
     * 일정 식별자와 계정 식별자는 담지 않습니다.
     * 앞엣것은 경로에서, 뒤엣것은 게이트웨이가 넣어 준 헤더에서 오므로
     * 컨트롤러가 따로 넘깁니다.
     */
    public ItineraryUpdateInput toInput() {
        return new ItineraryUpdateInput(visitAt, petIdProvided, petId, memoProvided, memo);
    }
}
