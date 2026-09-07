package com.pawtrail.user.domain.provider;

import com.pawtrail.user.domain.provider.dto.VerdictData;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * verdict 서비스에서 판정을 받아오는 약속입니다.
 *
 * 이 인터페이스에 HTTP 도 RestClient 도 나오지 않습니다.
 * 무엇을 받아오는지만 적고 어떻게 받아오는지는 infrastructure 가 정합니다.
 */
public interface VerdictProvider {

    /**
     * 여러 장소의 판정을 한 반려동물 기준으로 한 번에 받아옵니다.
     *
     * petId 가 단수인 것은 즐겨찾기가 대표 반려동물 한 마리를 기준으로 하기 때문입니다.
     * verdict 서비스는 마리 목록을 받지만 우리가 넘기는 것은 언제나 한 마리입니다.
     *
     * 대표가 없으면 이 메서드를 부르지 않습니다.
     * 판정할 기준이 없어 부를 이유가 없고, 부르는 쪽이 UNKNOWN 으로 채웁니다.
     *
     * 결과를 Map 으로 돌려주며 키가 없으면 판정을 못 받은 것입니다.
     * 부르는 쪽은 그 경우 배지를 비우고 준비물을 빈 목록으로 둡니다.
     *
     * 호출이 실패하면 빈 Map 을 돌려줍니다. 예외를 던지지 않습니다.
     * 판정 배지는 없어도 "내가 담아둔 곳 목록" 이라는 화면의 목적이 이뤄지기 때문입니다.
     *
     * 방문 기록의 판정은 반대로 다룹니다.
     * 그쪽은 방문 시점 스냅샷을 저장하는 자리라 틀린 값이 영구히 남습니다.
     * 같은 서비스를 부르면서도 실패 처리가 갈리는 이유입니다.
     */
    Map<UUID, VerdictData> findByPlaceIds(Collection<UUID> placeIds, UUID petId);

    /**
     * 여러 장소의 판정을 여러 반려동물 기준으로 한 번에 받아옵니다.
     *
     * 방문 기록 목록이 씁니다.
     * 3월에는 몽이와, 5월에는 달이와 갔을 수 있어 방문마다 기준이 다릅니다.
     * 위 메서드로는 그것을 표현할 수 없어 따로 둡니다.
     *
     * 마리 수가 늘어도 호출은 한 번입니다.
     * verdict 가 placeIds 와 petIds 를 함께 받아 장소마다 마리별 판정을 돌려줍니다.
     * 늘어나는 것은 규칙 계산과 응답 크기뿐이고 왕복이 늘지 않습니다.
     *
     * 결과가 두 겹입니다.
     * 바깥 키가 장소, 안쪽 키가 반려동물입니다.
     * 부르는 쪽이 그 방문의 장소와 펫으로 두 번 찾아 꺼냅니다.
     *
     * 어느 한쪽 키가 없으면 그 조합의 판정을 못 받은 것입니다.
     * 호출이 실패하면 빈 Map 을 돌려줍니다. 예외를 던지지 않습니다.
     *
     * 즐겨찾기가 쓰는 위 메서드와 나누어 둔 이유는 그쪽이 대표 한 마리로 확정되어
     * 두 겹을 매번 벗길 이유가 없기 때문입니다.
     */
    Map<UUID, Map<UUID, VerdictData>> findByPlaceIdsForPets(
            Collection<UUID> placeIds, Collection<UUID> petIds);
}
