package com.pawtrail.user.application.dto.output;

import java.util.List;
import java.util.UUID;

/**
 * 최근에 본 장소 카드 한 장입니다.
 *
 * 명세가 카드 모양을 즐겨찾기와 같다고 정해 두었습니다.
 * 다만 둘이 완전히 같지는 않습니다.
 *
 *   빼는 것    memo · createdAt   즐겨찾기 표의 컬럼이라 여기 없습니다.
 *   더하는 것  isFavorite         이 화면은 하트가 담기와 해제를 다 합니다.
 *
 * 즐겨찾기 목록이 그 값을 안 싣는 것은 목록 자체가 그 표에서 나오기 때문입니다.
 * 거기 있는 것이 곧 담긴 것이라 따로 물을 이유가 없습니다.
 * 여기는 목록이 다른 곳에서 나오므로 어느 것을 담아뒀는지 따로 확인해야 합니다.
 *
 * @param placeId       장소 식별자입니다. 하트를 누를 때 이 값을 씁니다.
 * @param name          장소 이름입니다.
 * @param placeType     카테고리입니다.
 * @param imageUrl      대표 사진입니다. 없을 수 있습니다.
 * @param verdict       지금 판정입니다. 대표 반려동물이 없으면 UNKNOWN 이고,
 *                      판정을 못 받아오면 null 입니다. 둘은 뜻이 다릅니다.
 * @param requiredItems 준비물입니다. 비어 있을 수 있고 null 은 되지 않습니다.
 * @param ratingAvg     평점 평균입니다. 후기가 없거나 못 받아오면 null 입니다.
 * @param isFavorite    하트를 채울지 판단하는 값입니다.
 */
public record RecentPlaceCardOutput(UUID placeId,
                                    String name,
                                    String placeType,
                                    String imageUrl,
                                    String verdict,
                                    List<String> requiredItems,
                                    Double ratingAvg,
                                    boolean isFavorite) {
}
