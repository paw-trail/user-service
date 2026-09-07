package com.pawtrail.user.application.dto.output;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 그날 일정 목록의 카드 한 장입니다.
 *
 * 값이 다섯 곳에서 옵니다.
 *
 *   itinerary_stop   stopId · placeId · visitAt · petId · visitOrder · memo
 *   place            name · imageUrl · lat · lon · placeType · supplyPoint
 *   verdict          verdict · requiredItems
 *   review           ratingAvg
 *   visit_log        visited · visitId
 *
 * 즐겨찾기와 같은 카드 컴포넌트를 쓰지만 record 는 따로 둡니다.
 * 같은 이름의 필드가 서로 다른 것을 뜻하기 때문입니다.
 *
 *   즐겨찾기   verdict          담아둔 곳의 지금 판정
 *   방문 기록   verdictAtVisit   기록한 시점의 판정.  안 바뀜
 *   일정      verdict          아직 안 간 곳이라 지금 판정.  즐겨찾기와 같은 성격
 *
 * isFavorite 이 없습니다.
 * 이 화면의 카드에는 하트가 없고 삭제 버튼만 있어 favorite 을 조회하지 않습니다.
 *
 * @param stopId        일정 식별자입니다. 수정·삭제할 때 프론트가 이 값을 보냅니다.
 * @param placeId       장소 식별자입니다.
 * @param name          장소 이름입니다. place 에서 옵니다.
 * @param imageUrl      대표 사진입니다. 없을 수 있습니다.
 * @param lat           위도입니다. 프론트가 지도 마커와 경로선을 그릴 때 씁니다.
 * @param lon           경도입니다.
 * @param placeType     카테고리입니다. 지도 마커 색을 가르는 값입니다.
 * @param supplyPoint   동선 중 보급 지점인지입니다. 지도 범례의 간식·용품점이 이 값입니다.
 * @param visitAt       방문 예정 일시입니다. 시각을 안 정했으면 그 날짜의 00:00 입니다.
 * @param petId         동반 예정 동물입니다. 없으면 null 입니다.
 * @param visitOrder    그날 안에서의 순서입니다. 시각이 같을 때의 앞뒤를 가릅니다.
 * @param memo          일정 메모입니다.
 * @param verdict       지금 판정입니다. 동반 동물이 없으면 UNKNOWN 이고,
 *                      판정을 못 받아오면 null 입니다. 둘은 뜻이 다릅니다.
 * @param requiredItems 준비물입니다. 비어 있을 수 있고 null 은 되지 않습니다.
 * @param ratingAvg     평점 평균입니다. 후기가 없거나 못 받아오면 null 입니다.
 * @param visited       이미 [다녀왔어요] 를 눌렀는지입니다. 지난 날짜 카드의 버튼 상태입니다.
 * @param visitId       그때 만들어진 방문 기록입니다. visited 가 false 면 null 입니다.
 */
public record ItineraryCardOutput(UUID stopId,
                                  UUID placeId,
                                  String name,
                                  String imageUrl,
                                  BigDecimal lat,
                                  BigDecimal lon,
                                  String placeType,
                                  boolean supplyPoint,
                                  LocalDateTime visitAt,
                                  UUID petId,
                                  Integer visitOrder,
                                  String memo,
                                  String verdict,
                                  List<String> requiredItems,
                                  Double ratingAvg,
                                  boolean visited,
                                  UUID visitId) {
}
