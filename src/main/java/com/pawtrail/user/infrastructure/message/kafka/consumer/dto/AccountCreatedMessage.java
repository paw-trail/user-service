package com.pawtrail.user.infrastructure.message.kafka.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * account.created 의 payload 를 받는 소비 전용 DTO 입니다.
 *
 * auth 의 AccountCreatedEvent 와 짝이지만 그 클래스를 공유하지 않습니다.
 * 공유하려면 발행자의 도메인 클래스를 공통 모듈에 올려야 하고,
 * 그러면 auth 가 필드를 하나 고칠 때마다 받는 쪽 전부가 함께 배포됩니다.
 * 이벤트는 서비스를 떼어 놓으려고 쓰는 것인데 그러면 다시 붙게 됩니다.
 *
 * DomainEvent 를 구현하지 않습니다.
 * 그 인터페이스는 토픽과 집합체 정보를 봉투에 담기 위한 것이라 발행자만 필요합니다.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) 가 필수입니다.
 * auth 가 payload 에 필드를 더해도 user 는 모르는 값을 무시하고 지나가야 합니다.
 * 없으면 발행자가 필드를 하나 늘리는 순간 소비가 전부 실패합니다.
 *
 * @param accountId 계정 식별자입니다. 그대로 user_profile 의 기본 키가 됩니다.
 * @param email     계정 이메일입니다. user 는 저장하지 않습니다.
 *                  저장하면 auth 가 탈퇴할 때 치환하는 값을 user 가 옛 값으로 들고 있게 되어,
 *                  개인정보가 끊긴 줄 알았는데 user 에 남습니다.
 *                  payload 에서 빼지 않는 것은 나중에 다른 서비스가 같은 이벤트를
 *                  구독할 수 있고, 받아서 안 쓰는 비용이 없기 때문입니다.
 * @param nickname  가입할 때 입력한 이름입니다. 소셜 가입은 null 로 옵니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountCreatedMessage(UUID accountId, String email, String nickname) {
}
