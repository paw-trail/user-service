package com.pawtrail.user.application.dto.input;

import java.util.UUID;

/**
 * 즐겨찾기에 담을 때 서비스가 받는 값입니다.
 *
 * 프론트가 보내는 요청 형태(FavoriteCreateRequest)와 나누어 둡니다.
 * 그쪽은 검증 애노테이션이 붙은 표현 계층의 것이고
 * 이쪽은 검증을 통과한 뒤의 값입니다.
 *
 * memo 는 선택입니다.
 * 명세가 요청 필드로 두었고 엔티티도 받게 되어 있으나
 * 화면에 입력 자리가 아직 없어 당분간 언제나 null 로 옵니다.
 * 받아 두지 않으면 화면이 생길 때 이 경로 전체를 고치게 됩니다.
 */
public record FavoriteCreateInput(UUID placeId, String memo) {
}
