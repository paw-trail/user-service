package com.pawtrail.user.domain.provider.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 언어 모델에게 넘길 하루치 재료입니다.
 *
 * 도메인이 모아 두면 구현이 그것을 JSON 으로 바꿔 보냅니다.
 * 무엇을 넘길지는 도메인의 판단이고 어떤 형태로 직렬화할지는 구현의 몫이라 나눴습니다.
 *
 * 후기가 비어 있어도 문장이 만들어집니다.
 * review 서비스가 아직 없어 지금은 대부분 그 상태이며,
 * 장소와 시각과 메모만으로도 그날이 어땠는지는 쓸 수 있습니다.
 *
 * @param visitDate 요약할 날짜입니다.
 * @param visits    그날의 방문입니다. 다녀왔다고 확인한 것만 담깁니다.
 * @param reviews   그날 쓴 후기입니다. 없으면 빈 목록입니다.
 */
public record SummaryData(LocalDate visitDate,
                          List<VisitMaterial> visits,
                          List<ReviewData> reviews) {

    /**
     * 방문 하나를 문장 재료로 옮긴 것입니다.
     *
     * 식별자를 담지 않습니다.
     * 모델에게 UUID 를 넘겨 봐야 문장에 쓸 수 없고 토큰만 먹습니다.
     *
     * @param name      장소 이름입니다.
     * @param placeType 카테고리입니다. "카페와 공원을" 처럼 묶어 쓸 때 쓰입니다.
     * @param visitedAt 다녀온 시각입니다. "오전 11시에" 처럼 쓰입니다.
     * @param memo      사용자가 남긴 메모입니다.
     *                  직접 쓴 문장이라 그날의 결이 드러나는 좋은 재료입니다.
     */
    public record VisitMaterial(String name,
                                String placeType,
                                String visitedAt,
                                String memo) {
    }
}
