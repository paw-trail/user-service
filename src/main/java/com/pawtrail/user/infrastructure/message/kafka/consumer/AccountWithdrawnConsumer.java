package com.pawtrail.user.infrastructure.message.kafka.consumer;

import com.pawtrail.common.message.EventEnvelope;
import com.pawtrail.common.message.inbox.InboxProcessor;
import com.pawtrail.user.application.service.AccountWithdrawnService;
import com.pawtrail.user.infrastructure.message.kafka.consumer.dto.AccountWithdrawnMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * auth 가 발행한 account.withdrawn 을 받아 이 서비스의 데이터를 정리합니다.
 *
 * 가입을 받는 AccountCreatedConsumer 와 짝입니다.
 * 그쪽은 payload 의 값으로 프로필을 만들고, 이쪽은 payload 를 열쇠로만 써서 지웁니다.
 *
 * 이 클래스는 감싸기만 하고 실제 로직은 AccountWithdrawnService 가 맡습니다.
 * 지우는 대상이 표 다섯과 Redis, 객체 저장소라 무게가 그쪽에 있습니다.
 * 두 소비자의 모양을 같게 두면 어느 쪽을 열어도 같은 자리를 보게 됩니다.
 *
 * 파라미터를 EventEnvelope<AccountWithdrawnMessage> 로 선언하는 것이 중요합니다.
 * 값 역직렬화는 StringDeserializer 라 문자열로 들어오고,
 * 공통 모듈이 등록한 RecordMessageConverter 가 이 파라미터 타입을 보고 변환합니다.
 * 그래서 서비스가 자기 RecordMessageConverter 를 만들면 안 됩니다.
 * 빈이 둘이 되어 @ConditionalOnMissingBean 이 풀리고 어느 쪽도 적용되지 않습니다.
 *
 * 예외를 잡지 않습니다.
 * 공통 모듈의 DefaultErrorHandler 가 1초 · 2초 · 4초 간격으로 세 번 다시 시도하고
 * 그래도 실패하면 account.withdrawn.dlq 로 보냅니다.
 *
 * 잡아서 넘기면 조용히 사라지는데 그 대가가 가입 쪽보다 큽니다.
 * 이 이벤트를 놓치면 auth 는 이메일과 제공자 식별자를 끊었는데
 * 이쪽에는 닉네임과 프로필 사진이 그대로 남습니다.
 * 끊은 줄 알았던 신원이 이 서비스에 남아 있게 되므로 개인정보 관점에서 그냥 넘길 수 없습니다.
 * DLQ 에 남아 있으면 관리자가 재발행할 수 있습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountWithdrawnConsumer {

    private static final String TOPIC = "account.withdrawn";

    private final InboxProcessor inboxProcessor;
    private final AccountWithdrawnService accountWithdrawnService;

    /**
     * 계정 탈퇴 이벤트를 처리합니다.
     *
     * processOnce 가 이미 @Transactional 이므로 여기에 또 붙이지 않습니다.
     * 붙이면 바깥 트랜잭션이 하나 더 생겨 경계가 흐려지고,
     * 커밋 이후로 미뤄 둔 Redis 와 객체 저장소 정리가 실제 커밋보다 먼저 실행됩니다.
     *
     * 같은 메시지가 두 번 와도 processed_event 의 기본 키 충돌로 걸러집니다.
     * 카프카가 at-least-once 라 재전송이 정상 동작입니다.
     *
     * 다른 식별자로 같은 계정의 탈퇴가 또 오는 경우는 서비스가 다룹니다.
     * 이미 삭제 표시가 있으면 프로필을 건드리지 않고 넘어갑니다.
     */
    @KafkaListener(topics = TOPIC)
    public void consume(EventEnvelope<AccountWithdrawnMessage> envelope) {
        AccountWithdrawnMessage message = envelope.data();

        log.info("account.withdrawn 수신: eventId={}, accountId={}",
                envelope.eventId(), message.accountId());

        inboxProcessor.processOnce(
                envelope.eventId(),
                TOPIC,
                () -> accountWithdrawnService.withdraw(message.accountId())
        );
    }
}
