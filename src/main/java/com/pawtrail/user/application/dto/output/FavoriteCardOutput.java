package com.pawtrail.user.application.dto.output;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 즐겨찾기 목록의 카드 한 장입니다.
 *
 * 값이 네 곳에서 옵니다.
 *
 *   favorite 표       placeId · memo · createdAt
 *   place            name · placeType · imageUrl
 *   verdict          verdict · requiredItems
 *   review           ratingAvg
 *
 * 그래서 엔티티만으로는 만들 수 없고 조립은 서비스가 합니다.
 * ProfileOutput 에 from 이나 of 를 두지 않은 것과 같은 이유입니다.
 *
 * 방문 기록과 일정도 같은 카드 컴포넌트를 쓰지만 record 는 따로 둡니다.
 * 방문 기록의 배지는 방문 시점 스냅샷이고 즐겨찾기는 지금 판정이라
 * 같은 이름의 필드가 서로 다른 것을 뜻하게 됩니다.
 *
 * @param placeId       장소 식별자입니다. 해제할 때 프론트가 이 값을 보냅니다.
 * @param name          장소 이름입니다. place 에서 옵니다.
 * @param placeType     카테고리입니다. 프론트가 이 값을 세어 칩을 만듭니다.
 * @param imageUrl      대표 사진입니다. 없을 수 있습니다.
 * @param verdict       지금 판정입니다.
 *                      대표 반려동물이 없으면 UNKNOWN 이고,
 *                      판정을 못 받아오면 null 입니다.
 *                      프론트는 GET /users/me 의 defaultPetId 로 그 둘을 가릅니다.
 * @param requiredItems 준비물입니다. 비어 있을 수 있고 null 은 되지 않습니다.
 * @param ratingAvg     평점 평균입니다. 후기가 없거나 못 받아오면 null 입니다.
 * @param memo          담을 때 적은 메모입니다. 화면에 입력 자리가 아직 없어 지금은 항상 null 입니다.
 * @param createdAt     즐겨찾기에 담은 시각입니다.
 */
public record FavoriteCardOutput(UUID placeId,
                                 String name,
                                 String placeType,
                                 String imageUrl,
                                 String verdict,
                                 List<String> requiredItems,
                                 Double ratingAvg,
                                 String memo,
                                 LocalDateTime createdAt) {
}
