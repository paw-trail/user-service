package com.pawtrail.user.infrastructure.provider.internal;

import com.pawtrail.common.response.CommonApiResponse;
import com.pawtrail.user.domain.provider.VerdictProvider;
import com.pawtrail.user.domain.provider.dto.VerdictData;
import com.pawtrail.user.infrastructure.provider.internal.dto.VerdictBatchResponse;
import java.util.ArrayList;
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
 * 도메인이 선언한 약속을 verdict 서비스 호출로 구현합니다.
 *
 * PlaceProviderImpl 과 같은 모양이며 부르는 방식만 다릅니다.
 * 장소 목록을 본문에 담아 보내야 해서 GET 이 아니라 POST 입니다.
 */
@Slf4j
@Component
public class VerdictProviderImpl implements VerdictProvider {

    private static final String BASE_URL = "lb://verdict-service";

    /**
     * 한 번에 보낼 수 있는 장소 수입니다.
     *
     * verdict 가 500건을 넘으면 400 을 냅니다.
     * 즐겨찾기 목록은 담기 상한을 두지 않기로 해서 그 수를 넘길 수 있으므로
     * 여기서 잘라 여러 번 부릅니다.
     *
     * 잘라 부르는 것이 부르는 쪽에 드러나지 않습니다.
     * 도메인은 장소 목록을 넘기고 판정 Map 을 받을 뿐입니다.
     */
    private static final int BATCH_SIZE = 500;

    private final RestClient restClient;

    /**
     * 빌더를 주입받아 RestClient 를 만듭니다.
     *
     * @Qualifier 를 반드시 붙여야 합니다.
     * 빠뜨리면 아무것도 얹히지 않은 @Primary 빌더가 조용히 주입되어
     * lb:// 를 풀지 못하고 호출하는 순간에 실패합니다.
     */
    public VerdictProviderImpl(
            @Qualifier("internalRestClientBuilder") RestClient.Builder builder) {

        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    /**
     * 여러 장소의 판정을 한 반려동물 기준으로 받아옵니다.
     *
     * 어느 한 묶음이 실패하면 전체를 포기하고 빈 Map 을 돌려줍니다.
     * 일부만 채워 넣으면 같은 화면에서 어떤 카드는 배지가 있고
     * 어떤 카드는 없는 상태가 되는데, 사용자가 그 차이를 조건의 차이로 읽습니다.
     * 배지가 전부 없는 것은 "판정을 못 불러왔다" 로 읽히지만
     * 일부만 없는 것은 "그 장소만 판정이 없다" 로 읽힙니다.
     */
    @Override
    public Map<UUID, VerdictData> findByPlaceIds(Collection<UUID> placeIds, UUID petId) {
        if (placeIds == null || placeIds.isEmpty() || petId == null) {
            return Map.of();
        }

        List<UUID> targets = new ArrayList<>(placeIds);
        Map<UUID, VerdictData> result = new LinkedHashMap<>();

        for (int from = 0; from < targets.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, targets.size());
            Map<UUID, VerdictData> chunk = requestChunk(targets.subList(from, to), petId);

            if (chunk == null) {
                return Map.of();
            }
            result.putAll(chunk);
        }
        return result;
    }

    /**
     * 한 묶음을 요청합니다. 실패하면 null 을 돌려줍니다.
     *
     * 빈 Map 이 아니라 null 인 것은 둘을 갈라야 하기 때문입니다.
     * 빈 Map 은 "물어봤는데 아무 판정도 없었다" 이고
     * null 은 "물어보지 못했다" 입니다.
     * 부르는 쪽이 앞엣것은 그대로 두고 뒤엣것은 전체를 포기합니다.
     */
    private Map<UUID, VerdictData> requestChunk(List<UUID> placeIds, UUID petId) {
        try {
            CommonApiResponse<VerdictBatchResponse> response = restClient.post()
                    .uri("/internal/verdicts/batch")
                    .body(Map.of(
                            "placeIds", placeIds.stream().map(UUID::toString).toList(),
                            "petIds", List.of(petId.toString())))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.getData() == null
                    || response.getData().results() == null) {
                log.warn("판정 응답이 비어 있습니다: 장소 {}건", placeIds.size());
                return null;
            }

            return toMap(response.getData().results());

        } catch (Exception e) {
            log.warn("판정을 받아오지 못했습니다: 장소 {}건, petId={}, reason={}",
                    placeIds.size(), petId, e.getMessage());
            return null;
        }
    }

    /**
     * 응답을 도메인 타입으로 바꿉니다.
     *
     * 마리별 판정 배열에서 하나를 꺼내는 일이 여기서 일어납니다.
     * 그것이 verdict 의 응답 형태를 아는 일이라 도메인이 할 일이 아닙니다.
     *
     * 우리가 넘긴 반려동물이 한 마리이므로 원소도 하나입니다.
     * 배열이 비어 있으면 그 장소의 판정이 없는 것이라 결과에서 뺍니다.
     *
     * requiredItems 가 null 로 오면 빈 목록으로 바꿉니다.
     * 도메인 쪽에서 null 인지 매번 확인하지 않게 하기 위해서입니다.
     */
    private Map<UUID, VerdictData> toMap(List<VerdictBatchResponse.Result> results) {
        Map<UUID, VerdictData> map = new LinkedHashMap<>();

        for (VerdictBatchResponse.Result result : results) {
            UUID placeId = parseUuidOrNull(result.placeId());
            if (placeId == null) {
                continue;
            }

            List<VerdictBatchResponse.PetVerdict> verdicts = result.verdicts();
            if (verdicts == null || verdicts.isEmpty()) {
                continue;
            }

            String verdict = verdicts.get(0).verdict();
            if (verdict == null) {
                continue;
            }

            List<String> requiredItems = result.requiredItems() == null
                    ? List.of()
                    : result.requiredItems();

            map.put(placeId, new VerdictData(verdict, requiredItems));
        }
        return map;
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("판정 응답의 장소 식별자가 UUID 형식이 아닙니다: {}", value);
            return null;
        }
    }
}
