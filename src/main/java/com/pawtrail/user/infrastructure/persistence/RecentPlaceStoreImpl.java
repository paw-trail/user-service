package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.repository.RecentPlaceStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 Redis 정렬 집합으로 구현합니다.
 *
 * 점수를 담은 시각으로 두고 큰 것부터 읽습니다.
 * 가장 최근에 본 것이 맨 앞이라는 뜻이며 화면도 그 순서로 보여 줍니다.
 *
 * 목록이 아니라 정렬 집합인 이유
 *
 * 이 기능이 지켜야 하는 것은 셋입니다.
 *   같은 장소가 두 번 들어가지 않을 것
 *   최근에 본 것이 앞에 올 것
 *   스무 곳까지만 남을 것
 *
 * 목록으로 하면 첫째를 스스로 지킬 수 없어 넣기 전에 지우는 단계가 붙습니다.
 * 그 두 단계 사이에 같은 요청이 하나 더 들어오면 둘 다 지우기를 끝낸 뒤
 * 각각 넣게 되어 같은 장소가 두 번 남습니다.
 *
 * 정렬 집합은 같은 값을 다시 넣으면 점수만 바뀝니다.
 * 지우고 넣는 두 단계가 애초에 없으므로 그 사이에 낄 자리도 없습니다.
 * 막는 것이 아니라 생길 수가 없습니다.
 *
 * 인기 급상승 목록도 같은 구조를 씁니다.
 * 장소 상세를 열 때 쌓고 순위 순으로 읽는다는 점이 이 기능과 같습니다.
 *
 * 수명을 두지 않습니다.
 * 오래됐다고 지울 이유가 없는 그 사람의 이력이고,
 * 계정당 식별자 스무 개라 아낄 메모리도 없습니다.
 * 대신 탈퇴할 때 반드시 지워야 합니다. 그것이 유일한 정리 경로입니다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RecentPlaceStoreImpl implements RecentPlaceStore {

    private static final String KEY_PREFIX = "recent:places:";

    /**
     * 남겨 두는 개수입니다.
     *
     * 스물로 둔 것은 화면이 몇 장을 보여줄지 프론트가 정하기 때문입니다.
     * 서버 상한이 그보다 넉넉해야 하고, 쉰까지 두면 최근이라는 말의 뜻이 흐려집니다.
     *
     * 설정으로 빼지 않았습니다.
     * 화면과 함께 정해지는 값이라 재배포 없이 바꿔서 얻을 것이 없고,
     * 바꾸면 이미 담긴 목록의 길이와 어긋나 오히려 헷갈립니다.
     */
    private static final int MAX_SIZE = 20;

    private final StringRedisTemplate redisTemplate;

    /**
     * 장소를 맨 앞에 놓습니다.
     *
     * 이미 있으면 점수만 새 시각으로 바뀝니다.
     * 원소는 그대로 하나이므로 같은 장소를 세 번 열어도 목록에 한 번만 나옵니다.
     *
     * 점수를 밀리초로 둡니다.
     * 사람이 장소 상세를 같은 밀리초에 두 개 열 수는 없으므로 순서가 어긋나지 않습니다.
     *
     * 자를 때 순위 범위를 씁니다.
     * 정렬 집합의 순위는 점수가 작은 것부터이므로 0 번이 가장 오래된 것이고,
     * 뒤에서 스무 개를 남기려면 앞에서부터 그만큼을 뺀 자리까지 지웁니다.
     * 원소가 스무 개 이하이면 지울 것이 없어 아무 일도 일어나지 않습니다.
     */
    @Override
    public void push(UUID accountId, UUID placeId) {
        String key = key(accountId);

        redisTemplate.opsForZSet().add(key, placeId.toString(), System.currentTimeMillis());
        redisTemplate.opsForZSet().removeRange(key, 0, -(MAX_SIZE + 1));
    }

    /**
     * 최근에 본 장소를 앞에서부터 돌려줍니다.
     *
     * 점수가 큰 것부터 읽습니다. 그것이 가장 최근에 본 것입니다.
     *
     * 끝 위치를 하나 빼서 넘깁니다.
     * 범위 조회가 양끝을 포함하므로 그대로 주면 하나가 더 옵니다.
     *
     * 돌려받은 집합의 순서를 믿어도 됩니다.
     * 정렬 집합의 범위 조회는 순서가 있는 결과를 주고 그것을 순서가 유지되는 집합에 담아 줍니다.
     *
     * 형식이 어긋난 값은 건너뜁니다.
     * 우리가 넣는 값이라 그럴 일이 없지만, 하나 때문에 목록 전체가 실패할 이유도 없습니다.
     */
    @Override
    public List<UUID> findRecent(UUID accountId, int size) {
        Set<String> values = redisTemplate.opsForZSet()
                .reverseRange(key(accountId), 0, size - 1);

        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<UUID> result = new ArrayList<>();
        for (String value : values) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                log.warn("최근 장소 목록에 형식이 어긋난 값이 있습니다: {}", value);
            }
        }
        return result;
    }

    /**
     * 그 사람의 목록을 통째로 지웁니다.
     *
     * 탈퇴를 처리할 때 부릅니다.
     * 이 목록에는 수명이 없으므로 이것이 유일한 정리 경로입니다.
     */
    @Override
    public void deleteAll(UUID accountId) {
        redisTemplate.delete(key(accountId));
    }

    private String key(UUID accountId) {
        return KEY_PREFIX + accountId;
    }
}
