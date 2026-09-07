package com.pawtrail.user.infrastructure.provider.internal;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.user.domain.provider.ReviewProvider;
import com.pawtrail.user.infrastructure.provider.internal.dto.ReviewCountResponse;
import com.pawtrail.user.infrastructure.provider.internal.dto.ReviewStatResponse;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 도메인이 선언한 약속을 review 서비스 호출로 구현합니다.
 *
 * 이 프로젝트에서 서비스가 서비스를 부르는 첫 자리였습니다.
 * PlaceProviderImpl 과 VerdictProviderImpl 도 같은 모양을 씁니다.
 *
 * internal 아래에 두는 것은 우리가 만든 다른 서비스이기 때문입니다.
 * external 은 카카오맵이나 기상청처럼 바깥 시스템을 부르는 자리입니다.
 */
@Slf4j
@Component
public class ReviewProviderImpl implements ReviewProvider {

    private static final String BASE_URL = "lb://review-service";
    private final RestClient restClient;

    /**
     * 빌더를 주입받아 RestClient 를 만듭니다.
     *
     * RestClient.builder() 를 직접 부르지 않습니다.
     * 그러면 인증 헤더도 lb:// 해석도 시간 제한도 붙지 않습니다.
     * 공통 모듈이 그 셋을 미리 걸어 둔 빌더를 내어 줍니다.
     *
     * @Qualifier 를 반드시 붙여야 합니다.
     * 같은 타입의 빈이 셋이고 그중 하나가 @Primary 입니다.
     * 빠뜨리면 아무것도 얹히지 않은 그 빌더가 조용히 주입되어
     * lb:// 를 풀지 못하고 기동이 아니라 호출하는 순간에 실패합니다.
     *
     * @RequiredArgsConstructor 를 쓰지 않는 것도 그 때문입니다.
     * 롬복이 만드는 생성자에는 @Qualifier 가 붙지 않습니다.
     *
     * baseUrl 은 여기서 한 번만 겁니다.
     * 빌더가 프로토타입이라 주입받을 때마다 새 인스턴스가 오므로
     * 다른 provider 의 빌더에 영향을 주지 않습니다.
     */
    public ReviewProviderImpl(
            @Qualifier("internalRestClientBuilder") RestClient.Builder builder) {

        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    /**
     * 그 사람이 쓴 후기 수를 받아옵니다.
     *
     * 실패하면 null 을 돌려주고 예외를 던지지 않습니다.
     * 마이페이지의 통계 한 칸이라 없어도 화면이 성립하기 때문입니다.
     *
     * 잡는 범위를 Exception 으로 둡니다.
     * 연결 거부, 시간 초과, 유레카가 서비스를 못 찾는 것,
     * 응답 형태가 다른 것까지 결과가 모두 같기 때문입니다.
     * 값을 못 받았다는 것 하나이고 부르는 쪽이 할 일도 하나입니다.
     *
     * 로그를 warn 으로 남깁니다.
     * 이 경로는 실패해도 사용자에게 아무 표시가 안 나므로
     * 로그가 없으면 숫자가 조용히 사라지는 것을 알아챌 방법이 없습니다.
     * 스택트레이스까지 남기면 review 가 없는 지금은 요청마다 쌓이므로
     * 메시지만 남깁니다.
     */
    @Override
    public Long countByAccountId(UUID accountId) {
        try {
            CommonApiResponse<ReviewCountResponse> response = restClient.get()
                    .uri(builder -> builder.path("/internal/reviews/count")
                            .queryParam("accountId", accountId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                log.warn("후기 수 응답이 비어 있습니다: accountId={}", accountId);
                return null;
            }

            return response.getData().count();

        } catch (Exception e) {
            log.warn("후기 수를 받아오지 못했습니다: accountId={}, reason={}",
                    accountId, e.getMessage());
            return null;
        }
    }

    /**
     * 여러 장소의 평점 평균을 받아옵니다.
     *
     * 위 메서드와 실패 처리가 같습니다.
     * 별점은 없어도 카드가 성립하므로 빈 Map 을 돌려주고 넘어갑니다.
     *
     * 평균이 null 인 원소는 결과에서 뺍니다.
     * 후기가 하나도 없는 장소가 그런 값으로 오는데,
     * Map 에 null 값을 담으면 부르는 쪽이 "키가 없음" 과 "값이 null" 을
     * 두 번 확인해야 합니다. 키가 없는 것 하나로 모읍니다.
     *
     * 건수 상한을 두지 않습니다.
     * verdict 와 달리 명세에 상한이 적혀 있지 않고,
     * 상한이 생기면 그때 이 메서드도 나눠 부르게 됩니다.
     */
    @Override
    public Map<UUID, Double> findRatingsByPlaceIds(Collection<UUID> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return Map.of();
        }

        try {
            CommonApiResponse<List<ReviewStatResponse>> response = restClient.get()
                    .uri(builder -> builder.path("/internal/reviews/stats")
                            .queryParam("placeIds", placeIds)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                log.warn("평점 응답이 비어 있습니다: 요청 {}건", placeIds.size());
                return Map.of();
            }

            return toMap(response.getData());

        } catch (Exception e) {
            log.warn("평점을 받아오지 못했습니다: 요청 {}건, reason={}",
                    placeIds.size(), e.getMessage());
            return Map.of();
        }
    }

    private Map<UUID, Double> toMap(List<ReviewStatResponse> responses) {
        Map<UUID, Double> result = new LinkedHashMap<>();

        for (ReviewStatResponse response : responses) {
            if (response.ratingAvg() == null) {
                continue;
            }
            UUID placeId = parseUuidOrNull(response.placeId());
            if (placeId == null) {
                continue;
            }
            result.put(placeId, response.ratingAvg());
        }
        return result;
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("평점 응답의 장소 식별자가 UUID 형식이 아닙니다: {}", value);
            return null;
        }
    }
}
