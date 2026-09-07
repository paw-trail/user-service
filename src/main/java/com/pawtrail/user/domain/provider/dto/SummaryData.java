package com.pawtrail.user.domain.provider.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 언어 모델에게 넘길 하루치 재료입니다.
 *
 * 도메인이 모아 두면 구현이 그것을 JSON 으로 바꿔 보냅니다.
 * 무엇을 넘길지는 도메인의 판단이고 어떤 형태로 직렬화할지는 구현의 몫이라 나눴습니다.
 *
 * 다녀온 곳과 담아만 둔 곳을 목록으로 갈라 둡니다.
 *
 * 한 목록에 담고 다녀왔는지를 필드로 표시하는 방법도 있었습니다.
 * 그러면 모델이 그 필드를 흘렸을 때 안 간 곳이 다녀온 것으로 쓰이는데,
 * 사용자가 자기 기록을 잘못 기억하게 되는 종류의 잘못이라 확률에 맡길 자리가 아닙니다.
 * 목록 자체가 갈려 있으면 필드 하나를 흘리는 것보다 헷갈리기 어렵습니다.
 *
 * 추론에 힘을 더 쓰게 해도 이 문제는 안 풀립니다.
 * 그 설정은 답을 내기 전에 얼마나 생각할지이지 지시를 얼마나 잘 지킬지가 아닙니다.
 *
 * 후기가 비어 있어도 문장이 만들어집니다.
 * 후기 서비스가 아직 없어 지금은 대부분 그 상태이며,
 * 장소와 시각과 메모만으로도 그날이 어땠는지는 쓸 수 있습니다.
 *
 * @param visitDate 요약할 날짜입니다.
 * @param visited   실제로 다녀온 곳입니다. 다녀왔다고 확인한 것만 담깁니다.
 * @param planned   담아만 두고 다녀왔는지 알 수 없는 곳입니다.
 * @param reviews   그날 쓴 후기입니다. 없으면 빈 목록입니다.
 */
public record SummaryData(LocalDate visitDate,
                          List<PlaceMaterial> visited,
                          List<PlaceMaterial> planned,
                          List<ReviewData> reviews) {

    /**
     * 장소 하나를 문장 재료로 옮긴 것입니다.
     *
     * 식별자를 담지 않습니다.
     * 모델에게 식별자를 넘겨 봐야 문장에 쓸 수 없고 토큰만 먹습니다.
     *
     * @param name      장소 이름입니다.
     * @param placeType 카테고리입니다. "카페와 공원을" 처럼 묶어 쓸 때 쓰입니다.
     * @param at        시각입니다. 다녀온 곳은 다녀온 시각이고 담아 둔 곳은 갈 예정이던 시각입니다.
     * @param memo      사용자가 남긴 메모입니다.
     *                  직접 쓴 문장이라 그날의 결이 드러나는 좋은 재료입니다.
     */
    public record PlaceMaterial(String name,
                                String placeType,
                                String at,
                                String memo) {
    }
}
