package com.pawtrail.user.domain.provider.dto;

import java.util.List;

/**
 * verdict 서비스에서 받아온 장소 하나의 판정입니다.
 *
 * 카드가 쓰는 것은 배지와 준비물 둘뿐이라 그 둘만 담습니다.
 * 응답에 함께 오는 hasConflict 와 evidenceSummary 는 목록 카드에 쓰이지 않습니다.
 *
 * verdict 가 단수인 것에 유의합니다.
 * verdict 서비스는 마리별 판정을 배열로 돌려주지만
 * 즐겨찾기는 대표 반려동물 한 마리를 기준으로 하기로 정했습니다.
 * 배열에서 그 한 마리를 꺼내는 일은 VerdictProviderImpl 이 합니다.
 * 그것이 verdict 의 응답 형태를 아는 일이라 도메인이 알 것이 아니기 때문입니다.
 *
 * 여러 마리 기준으로 열게 되면 이 record 와 구현만 바뀌고
 * 그때는 화면과 명세도 함께 바뀌는 시점입니다.
 */
public record VerdictData(String verdict, List<String> requiredItems) {
}
