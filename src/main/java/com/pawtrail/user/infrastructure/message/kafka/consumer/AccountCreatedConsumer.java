package com.pawtrail.user.infrastructure.message.kafka.consumer;

import com.pawtrail.common.message.EventEnvelope;
import com.pawtrail.common.message.inbox.InboxProcessor;
import com.pawtrail.user.application.service.UserProfileService;
import com.pawtrail.user.infrastructure.message.kafka.consumer.dto.AccountCreatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * auth 가 발행한 account.created 를 받아 프로필을 만듭니다.
 *
 * 이 서비스의 첫 카프카 리스너입니다.
 * auth 는 발행만 했으므로 공통 모듈의 소비 쪽 코드가 여기서 처음 돌아갑니다.
 *
 * 이 클래스는 감싸기만 하고 실제 로직은 UserProfileService 가 맡습니다.
 * 지금은 로직이 짧지만 탈퇴 소비는 다섯 표를 지우고 S3 객체까지 지웁니다.
 * 그때 가서 구조를 바꾸면 두 소비자의 모양이 갈립니다.
 *
 * 파라미터를 EventEnvelope<AccountCreatedMessage> 로 선언하는 것이 중요합니다.
 * 값 역직렬화는 StringDeserializer 라 문자열로 들어오고,
 * 공통 모듈이 등록한 RecordMessageConverter 가 이 파라미터 타입을 보고 변환합니다.
 * 그래서 서비스가 자기 RecordMessageConverter 를 만들면 안 됩니다.
 * 빈이 둘이 되어 @ConditionalOnMissingBean 이 풀리고 어느 쪽도 적용되지 않습니다.
 *
 * 예외를 잡지 않습니다.
 * 공통 모듈의 DefaultErrorHandler 가 1초 · 2초 · 4초 간격으로 세 번 다시 시도하고
 * 그래도 실패하면 account.created.dlq 로 보냅니다.
 * 검증 실패처럼 다시 해도 소용없는 경우까지 그대로 두는 이유는,
 * 잡아서 넘기면 조용히 사라지기 때문입니다.
 * 이 이벤트를 놓치면 그 사람은 프로필이 영영 생기지 않고 GET /users/me 가 계속 404 입니다.
 * DLQ 에 남아 있으면 관리자가 재발행할 수 있습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreatedConsumer {

    private static final String TOPIC = "account.created";

    private final InboxProcessor inboxProcessor;
    private final UserProfileService userProfileService;

    /**
     * 계정 생성 이벤트를 처리합니다.
     *
     * processOnce 가 이미 @Transactional 이므로 여기에 또 붙이지 않습니다.
     * 붙이면 바깥 트랜잭션이 하나 더 생겨 경계가 흐려집니다.
     *
     * 같은 메시지가 두 번 와도 processed_event 의 기본 키 충돌로 걸러집니다.
     * 카프카가 at-least-once 라 재전송이 정상 동작입니다.
     */
    @KafkaListener(topics = TOPIC)
    public void consume(EventEnvelope<AccountCreatedMessage> envelope) {
        AccountCreatedMessage message = envelope.data();

        log.info("account.created 수신: eventId={}, accountId={}",
                envelope.eventId(), message.accountId());

        inboxProcessor.processOnce(
                envelope.eventId(),
                TOPIC,
                () -> userProfileService.createFromAccountCreated(
                        message.accountId(), message.nickname())
        );
    }
}
