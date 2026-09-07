package com.pawtrail.user.presentation.request;

import com.pawtrail.user.application.dto.input.FavoriteCreateInput;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 즐겨찾기 담기 요청입니다.
 *
 * 누가 담는지는 받지 않습니다.
 * 게이트웨이가 넣은 X-User-Id 를 컨트롤러가 @CurrentUser 로 꺼내 씁니다.
 * 요청에 accountId 를 두면 남의 즐겨찾기에 넣을 수 있게 됩니다.
 *
 * 장소가 실재하는지는 검사하지 않습니다.
 * 담은 뒤에 장소가 사라지는 경우를 그 검사로는 막지 못하고,
 * 목록 조회에서 없는 것을 걸러내면 두 경우가 함께 처리됩니다.
 *
 * @param placeId 담을 장소입니다.
 * @param memo    선택입니다. 화면에 입력 자리가 아직 없어 지금은 언제나 null 로 옵니다.
 *                명세가 요청 필드로 두었고 엔티티도 받게 되어 있어 미리 받습니다.
 *                안 받아 두면 화면이 생길 때 이 경로 전체를 고치게 됩니다.
 */
public record FavoriteCreateRequest(

        @NotNull(message = "placeId 를 보내 주세요")
        UUID placeId,

        // 컬럼이 varchar(200) 이라 길이를 맞춤
        // 여기서 안 막으면 DB 가 막는데, 그때는 어느 필드가 문제인지 응답에 안 실림
        @Size(max = 200, message = "메모는 200자까지 쓸 수 있습니다")
        String memo
) {

    public FavoriteCreateInput toInput() {
        return new FavoriteCreateInput(placeId, memo);
    }
}
