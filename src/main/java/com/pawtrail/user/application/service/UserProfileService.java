package com.pawtrail.user.application.service;

import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 프로필을 다루는 서비스입니다.
 *
 * 지금은 이벤트로 만드는 것뿐이지만 조회와 수정도 이 클래스에 모읍니다.
 * auth 도 AccountService 하나에 계정 관련 동작을 모았습니다.
 *
 * 이 클래스에 @Transactional 을 클래스 단위로 붙이지 않습니다.
 * 이벤트 경로는 InboxProcessor.processOnce 가 이미 트랜잭션을 열고 있어
 * 여기에 또 붙이면 경계가 어디인지 읽는 사람이 매번 따져야 합니다.
 * API 경로에서 부르는 메서드가 생기면 그 메서드에만 붙입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    /**
     * account.created 를 받아 프로필을 만듭니다.
     *
     * 이미 있으면 아무 것도 하지 않고 넘어갑니다.
     * 두 가지 경우가 여기에 걸립니다.
     *
     *   같은 이벤트가 두 번 처리되려는 경우
     *     processed_event 가 먼저 걸러 주므로 사실상 오지 않지만,
     *     기본 키 충돌로 실패하는 것보다 조용히 넘어가는 편이 낫습니다.
     *
     *   탈퇴가 먼저 처리된 경우
     *     account.created 가 발행에 실패해 멈춰 있는 사이 사용자가 탈퇴하면
     *     account.withdrawn 이 먼저 도착합니다.
     *     그때 탈퇴 소비자가 account_id 만 채우고 deleted_at 을 찍은 행을 만들어 두므로,
     *     나중에 재발행된 account.created 가 도착해도 여기서 멈춥니다.
     *     막지 않으면 탈퇴한 계정의 프로필이 뒤늦게 생겨 아무 API 에도 안 잡히는
     *     고아 행으로 남습니다.
     *
     * 존재 확인에 findById 를 쓰지 않는 이유는 UserProfile 에 걸린
     * @SQLRestriction("deleted_at IS NULL") 이 삭제 표시 행을 가리기 때문입니다.
     * 그 제한을 우회하는 existsIncludingDeleted 를 씁니다.
     */
    public void createFromAccountCreated(UUID accountId, String nickname) {
        if (userProfileRepository.existsIncludingDeleted(accountId)) {
            log.info("이미 처리된 계정입니다. 프로필을 만들지 않습니다: accountId={}", accountId);
            return;
        }

        userProfileRepository.save(UserProfile.create(accountId, nickname));

        log.info("프로필을 만들었습니다: accountId={}", accountId);
    }
}
