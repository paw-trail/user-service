package com.pawtrail.user.infrastructure.persistence;

import com.pawtrail.user.domain.repository.SummaryRateLimitStore;
import com.pawtrail.user.infrastructure.config.LlmProperties;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 Redis 로 구현합니다.
 *
 * 값이 저절로 사라져야 하는 성격이라 Redis 가 맞습니다.
 * 데이터베이스에 두면 지난 기록을 지우는 작업을 따로 만들어야 하고,
 * 남길 가치도 없는 이력이 쌓입니다.
 *
 * 이 서비스가 Redis 를 쓰는 첫 자리입니다.
 * 인증 쪽의 메일 발송 제한이 같은 구조를 쓰고 있어 그것을 따랐습니다.
 *
 * 키 접두어를 기능별로 가릅니다.
 *   summary:cooldown:{계정}:{요약 대상 날짜}
 *   summary:limit:{계정}:{오늘}
 * 섞으면 서로 다른 제한이 한 키를 나눠 쓰게 되어 관계없는 것끼리 막습니다.
 */
@Repository
@RequiredArgsConstructor
public class SummaryRateLimitStoreImpl implements SummaryRateLimitStore {

    private static final String COOLDOWN_PREFIX = "summary:cooldown:";
    private static final String LIMIT_PREFIX = "summary:limit:";

    private final StringRedisTemplate redisTemplate;
    private final LlmProperties properties;

    /**
     * 하루 한도를 하나 씁니다.
     *
     * 세고 나서 판단합니다.
     * increment 는 Redis 안에서 한 번에 처리되므로 같은 순간에 백 개가 들어와도
     * 각자 다른 값을 받습니다. 읽고 나서 올리면 전부 같은 값을 읽어 다 통과합니다.
     *
     * 쿨다운이 앞에서 막아 주지 않습니다.
     * 인증 메일 쪽은 쿨다운이 주소별이라 그것이 한 번에 하나만 통과시켜 주지만,
     * 여기 쿨다운은 요약 대상 날짜별이라 다른 날짜끼리는 겹치지 않습니다.
     *
     * 한도를 넘긴 요청도 값을 올립니다.
     * 그 수가 계속 늘지만 자정에 키가 통째로 사라지므로 남지 않습니다.
     */
    @Override
    public boolean tryConsumeDailyLimit(UUID accountId) {
        String key = limitKey(accountId);
        Long count = redisTemplate.opsForValue().increment(key);

        // increment 는 키가 없으면 만들면서 수명을 주지 않음
        // 그대로 두면 이 키만 영원히 남고 하루 제한이 평생 제한이 됨
        //
        // 처음 만들어질 때만 수명을 붙임
        // 자정까지 남은 시간을 주므로 날이 바뀌면 카운터가 통째로 사라짐
        if (count != null && count == 1L) {
            redisTemplate.expire(key, untilMidnight());
        }

        return count != null && count <= properties.dailyLimit();
    }

    /**
     * 그 날짜의 쿨다운 자리를 잡습니다.
     *
     * setIfAbsent 는 "없을 때만 넣는다" 를 Redis 안에서 한 번에 처리합니다.
     * 확인과 설정을 나누면 그 사이에 다른 요청이 끼어 둘 다 통과합니다.
     *
     * 값은 쓰지 않으므로 아무것이나 넣습니다.
     * 중요한 것은 값이 아니라 이 키를 내가 만들었는가이며,
     * 그 판단이 한 번에 끝나는 것이 이 방식의 전부입니다.
     */
    @Override
    public boolean tryAcquireCooldown(UUID accountId, LocalDate visitDate) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                cooldownKey(accountId, visitDate),
                "1",
                Duration.ofSeconds(properties.cooldownSeconds()));

        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 잡아 둔 쿨다운을 돌려줍니다.
     *
     * 하루 한도는 건드리지 않습니다.
     * 실패한 호출도 모델을 부른 것은 맞으므로 세는 편이 사실에 가깝고,
     * 되돌리면 그 사이에 들어온 요청과 값이 어긋납니다.
     */
    @Override
    public void releaseCooldown(UUID accountId, LocalDate visitDate) {
        redisTemplate.delete(cooldownKey(accountId, visitDate));
    }

    private String cooldownKey(UUID accountId, LocalDate visitDate) {
        return COOLDOWN_PREFIX + accountId + ":" + visitDate;
    }

    /**
     * 오늘 날짜로 키를 만듭니다.
     *
     * 요약 대상 날짜가 아니라 오늘입니다.
     * 하루 한도는 사용자가 오늘 얼마나 썼는가이지 어느 날을 요약했는가가 아닙니다.
     * 지난 여행을 몰아서 요약하는 것도 같은 한도 안에서 이뤄집니다.
     */
    private String limitKey(UUID accountId) {
        return LIMIT_PREFIX + accountId + ":" + LocalDate.now();
    }

    /**
     * 자정까지 남은 시간을 돌려줍니다.
     *
     * 고정된 스물네 시간이 아닙니다.
     * 그렇게 두면 창이 마지막 요청 기준으로 밀려나 "하루" 의 뜻이 사람마다 달라집니다.
     * 자정에 맞추면 날짜 키와 수명이 같은 시점에 끝나 계산이 어긋나지 않습니다.
     */
    private Duration untilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay());
    }
}
