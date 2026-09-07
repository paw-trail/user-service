package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.repository.RecentPlaceStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 Redis 목록으로 구현합니다.
 *
 * 목록은 앞에서 넣고 앞에서 읽습니다.
 * 가장 최근에 본 것이 맨 앞이라는 뜻이며, 화면도 그 순서로 보여 줍니다.
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
     * 먼저 지우고 넣습니다.
     * 이미 있는 것을 그대로 두고 앞에만 더하면 같은 장소가 목록에 여러 번 나옵니다.
     * 카페 하나를 세 번 열면 목록이 그 장소로 도배됩니다.
     *
     * 지우는 개수를 0 으로 주면 일치하는 것을 전부 지웁니다.
     *
     * 마지막에 잘라 상한을 지킵니다.
     * 넣을 때마다 자르므로 목록이 그 길이를 넘는 순간이 없습니다.
     */
    @Override
    public void push(UUID accountId, UUID placeId) {
        String key = key(accountId);
        String value = placeId.toString();

        redisTemplate.opsForList().remove(key, 0, value);
        redisTemplate.opsForList().leftPush(key, value);
        redisTemplate.opsForList().trim(key, 0, MAX_SIZE - 1);
    }

    /**
     * 최근에 본 장소를 앞에서부터 돌려줍니다.
     *
     * 끝 위치를 하나 빼서 넘깁니다.
     * Redis 의 범위 조회는 양끝을 포함하므로 그대로 주면 하나가 더 옵니다.
     *
     * 형식이 어긋난 값은 건너뜁니다.
     * 우리가 넣는 값이라 그럴 일이 없지만, 하나 때문에 목록 전체가 실패할 이유도 없습니다.
     */
    @Override
    public List<UUID> findRecent(UUID accountId, int size) {
        List<String> values = redisTemplate.opsForList().range(key(accountId), 0, size - 1);

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
