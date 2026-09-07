package com.pawtrail.user.infrastructure.provider.internal;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.user.domain.provider.PlaceProvider;
import com.pawtrail.user.domain.provider.dto.PlaceData;
import com.pawtrail.user.infrastructure.provider.internal.dto.PlaceResponse;
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
 * 도메인이 선언한 약속을 place 서비스 호출로 구현합니다.
 *
 * ReviewProviderImpl 과 같은 모양입니다.
 * internal 아래에 두는 것은 우리가 만든 다른 서비스이기 때문입니다.
 * external 은 카카오맵이나 기상청처럼 바깥 시스템을 부르는 자리입니다.
 */
@Slf4j
@Component
public class PlaceProviderImpl implements PlaceProvider {

    private static final String BASE_URL = "lb://place-service";
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
     */
    public PlaceProviderImpl(
            @Qualifier("internalRestClientBuilder") RestClient.Builder builder) {

        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    /**
     * 여러 장소를 한 번에 받아옵니다.
     *
     * 빈 목록으로 부르면 호출하지 않습니다.
     * 즐겨찾기가 하나도 없는 사람이 목록을 열 때가 그 경우인데,
     * 물어볼 것이 없는 요청을 보낼 이유가 없습니다.
     *
     * 잡는 범위를 Exception 으로 둡니다.
     * 연결 거부, 시간 초과, 유레카가 서비스를 못 찾는 것,
     * 응답 형태가 다른 것까지 결과가 모두 같기 때문입니다.
     * 값을 못 받았다는 것 하나이고 부르는 쪽이 할 일도 하나입니다.
     *
     * 실패에 null 을 돌려줍니다.
     * 빈 Map 으로 돌려주면 "장소가 다 없어졌다" 와 구분되지 않는데,
     * 부르는 쪽이 앞엣것은 빈 목록으로 내려보내고 뒤엣것은 요청을 실패시킵니다.
     *
     * 로그를 warn 으로 남깁니다.
     * 지금은 place 서비스가 없어 언제나 이 경로로 옵니다.
     * 스택트레이스까지 남기면 요청마다 쌓이므로 메시지만 남깁니다.
     */
    @Override
    public Map<UUID, PlaceData> findByIds(Collection<UUID> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return Map.of();
        }

        try {
            CommonApiResponse<List<PlaceResponse>> response = restClient.get()
                    .uri(builder -> builder.path("/internal/places")
                            .queryParam("ids", placeIds)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null) {
                log.warn("장소 응답이 비어 있습니다: 요청 {}건", placeIds.size());
                return null;
            }

            return toMap(response.getData());

        } catch (Exception e) {
            log.warn("장소를 받아오지 못했습니다: 요청 {}건, reason={}",
                    placeIds.size(), e.getMessage());
            return null;
        }
    }

    /**
     * 응답을 도메인 타입으로 바꿔 placeId 로 찾을 수 있게 담습니다.
     *
     * 식별자가 없거나 형식이 어긋난 원소는 건너뜁니다.
     * 그런 원소는 부르는 쪽이 자기 목록과 맞출 수 없어 쓸 데가 없고,
     * 하나 때문에 목록 전체를 실패시킬 이유도 없습니다.
     *
     * LinkedHashMap 을 쓰는 것은 응답 순서를 보존하기 위해서입니다.
     * 지금은 부르는 쪽이 자기 순서대로 조립하므로 쓰이지 않지만,
     * 순서가 뒤섞이는 자료구조를 골라 둘 이유도 없습니다.
     */
    private Map<UUID, PlaceData> toMap(List<PlaceResponse> responses) {
        Map<UUID, PlaceData> result = new LinkedHashMap<>();

        for (PlaceResponse response : responses) {
            UUID placeId = parseUuidOrNull(response.placeId());
            if (placeId == null) {
                continue;
            }
            result.put(placeId, new PlaceData(
                    placeId,
                    response.name(),
                    response.placeType(),
                    response.imageUrl(),
                    response.lat(),
                    response.lon()));
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
            log.warn("장소 식별자가 UUID 형식이 아닙니다: {}", value);
            return null;
        }
    }
}
