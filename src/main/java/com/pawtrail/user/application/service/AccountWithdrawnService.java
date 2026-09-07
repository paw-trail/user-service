package com.pawtrail.user.application.service;

import com.pawtrail.common.audit.AuditorProvider;
import com.pawtrail.user.application.support.AfterCommitExecutor;
import com.pawtrail.user.domain.model.UserProfile;
import com.pawtrail.user.domain.provider.StorageProvider;
import com.pawtrail.user.domain.repository.DailySummaryRepository;
import com.pawtrail.user.domain.repository.FavoriteRepository;
import com.pawtrail.user.domain.repository.ItineraryStopRepository;
import com.pawtrail.user.domain.repository.RecentPlaceStore;
import com.pawtrail.user.domain.repository.UserProfileRepository;
import com.pawtrail.user.domain.repository.VisitLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 탈퇴한 계정에 대해 이 서비스가 가진 것을 정리합니다.
 *
 * account.withdrawn 을 받는 경로에서만 불립니다. 사용자가 직접 부르는 API 는 없습니다.
 * 탈퇴 자체는 auth 가 처리하고 이쪽은 그 결과를 받아 자기 몫을 지웁니다.
 *
 * UserProfileService 에 넣지 않은 이유
 *
 * 그 클래스는 프로필 하나를 보고 고치는 일을 모아 둔 곳이고 이미 여섯을 주입받고 있습니다.
 * 탈퇴는 표 다섯과 Redis, 객체 저장소를 가로지르는 동작이라 거기에 얹으면
 * 프로필 담당 클래스가 일정과 하루 요약까지 지우게 되고 주입도 열 안팎으로 늘어납니다.
 * auth 도 같은 이유로 AccountService 에서 WithdrawService 를 갈라 두었습니다.
 *
 * 이름을 auth 의 WithdrawService 와 다르게 둔 것은 하는 일이 반대이기 때문입니다.
 * 그쪽은 탈퇴시키는 서비스이고 이쪽은 탈퇴에 반응해 지우는 쪽이라 이벤트 이름을 따랐습니다.
 *
 * @Transactional 을 붙이지 않습니다.
 * 이벤트 경로는 InboxProcessor.processOnce 가 이미 트랜잭션을 열고 있어
 * 여기에 또 붙이면 경계가 어디인지 읽는 사람이 매번 따져야 합니다.
 * UserProfileService 가 이벤트 경로에만 애노테이션을 두지 않은 것과 같습니다.
 *
 * 트랜잭션 없이 불리면 아래 벌크 삭제가 그 자리에서 실패합니다.
 * 조용히 지나가지 않으므로 경계가 사라진 것을 모르고 넘어갈 일은 없습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountWithdrawnService {

    private final UserProfileRepository userProfileRepository;
    private final FavoriteRepository favoriteRepository;
    private final VisitLogRepository visitLogRepository;
    private final ItineraryStopRepository itineraryStopRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final RecentPlaceStore recentPlaceStore;
    private final StorageProvider storageProvider;
    private final AuditorProvider auditorProvider;
    private final AfterCommitExecutor afterCommitExecutor;

    /**
     * 그 계정의 프로필을 정리하고 나머지 데이터를 지웁니다.
     *
     * 하는 일이 둘로 나뉩니다. 프로필을 어떻게 다룰지는 상태에 따라 갈리고,
     * 나머지 정리는 상태와 무관하게 언제나 같습니다.
     *
     * 프로필 상태가 셋인 이유
     *
     *   정상          평범한 탈퇴입니다. 익명화하고 삭제 시각을 찍습니다.
     *   이미 탈퇴 표시  같은 계정에 대해 두 번째로 도착한 경우이거나,
     *                 순서 역전 방어로 만들어 둔 표시 행이 이미 있는 경우입니다.
     *                 프로필은 건드리지 않습니다. 다시 익명화하면 삭제 시각만 흔들립니다.
     *   행이 아예 없음  account.created 가 아직 처리되지 않은 상태에서 탈퇴가 먼저 온 것입니다.
     *                 표시 행을 만들어 두어야 나중에 도착한 생성 이벤트가 멈춥니다.
     *
     * 세 갈래를 조회 한 번으로 가릅니다.
     * findById 는 삭제 표시 행을 걸러 내므로 뒤의 둘이 똑같이 비어 보입니다.
     *
     * 정리를 갈래 밖에 두는 이유
     *
     * 프로필이 없어도 다른 표에는 행이 있을 수 있습니다.
     * 쓰기 경로가 프로필 존재를 확인하지 않기 때문입니다.
     * 가입 직후 생성 이벤트가 발행되지 못한 채로 사용자가 로그인해 있으면
     * 프로필 조회는 실패하지만 즐겨찾기 담기는 성공합니다.
     *
     * 그래서 프로필 상태와 다른 표의 상태는 서로 독립입니다.
     * 정리를 갈래에 매달면, 표시 행만 만들고 끝낸 계정의 즐겨찾기가 그대로 남고
     * 그것을 지울 기회가 다시 오지 않습니다. auth 가 같은 이벤트를 두 번 내보내지 않기 때문입니다.
     *
     * 프로필을 먼저 다루는 이유
     *
     * 벌크 삭제가 영속성 컨텍스트를 우회하므로 순서가 보이는 대로 나가야 합니다.
     * 벌크 쿼리에 flushAutomatically 를 켜 두어 이 시점의 변경이 먼저 반영됩니다.
     *
     * 정상 경로에서 save 를 부르지 않습니다.
     * 조회해 온 엔티티라 변경 감지가 커밋 시점에 UPDATE 를 냅니다.
     * 표시 행만 새로 만든 것이라 저장이 필요합니다.
     */
    public void withdraw(UUID accountId) {
        String deletedBy = auditorProvider.current();

        UserProfile profile = userProfileRepository.findByIdIncludingDeleted(accountId)
                .orElse(null);

        if (profile == null) {
            userProfileRepository.save(UserProfile.withdrawnMarker(accountId, deletedBy));
            log.info("프로필이 없어 탈퇴 표시 행을 만들었습니다: accountId={}", accountId);

        } else if (profile.isDeleted()) {
            log.info("이미 탈퇴 처리된 계정입니다. 프로필은 그대로 둡니다: accountId={}", accountId);

        } else {
            profile.anonymize(deletedBy);
            log.info("프로필을 익명화했습니다: accountId={}", accountId);
        }

        deleteOwnedRows(accountId);
        scheduleExternalCleanup(accountId);
    }

    /**
     * 그 계정이 가진 표 넷을 지웁니다.
     *
     * 지운 행 수를 한 줄로 남깁니다.
     * 이벤트 소비는 응답이 없어 무엇이 얼마나 지워졌는지를 로그로만 확인할 수 있습니다.
     * 표마다 나눠 찍으면 한 번의 탈퇴가 로그 넉 줄이 되어 오히려 읽기 어렵습니다.
     *
     * 전부 0 이어도 정상입니다.
     * 담아 둔 것이 하나도 없는 계정이 있고, 표시 행만 만드는 경로도 대개 그렇습니다.
     */
    private void deleteOwnedRows(UUID accountId) {
        int favorites = favoriteRepository.deleteAllByAccountId(accountId);
        int visits = visitLogRepository.deleteAllByAccountId(accountId);
        int stops = itineraryStopRepository.deleteAllByAccountId(accountId);
        int summaries = dailySummaryRepository.deleteAllByAccountId(accountId);

        log.info("표를 정리했습니다: accountId={}, favorite={}, visitLog={},"
                        + " itineraryStop={}, dailySummary={}",
                accountId, favorites, visits, stops, summaries);
    }

    /**
     * 트랜잭션 밖에 있는 것을 커밋 이후에 지우도록 걸어 둡니다.
     *
     * 최근 장소는 Redis 에 있고 수명을 두지 않았습니다.
     * 여기서 지우지 않으면 그 키가 영영 남습니다.
     *
     * 프로필 사진은 계정마다 자리가 하나로 고정이라 주소를 받지 않고 계산해서 지웁니다.
     * 프로필이 없거나 사진을 올린 적이 없어도 그대로 부릅니다.
     * 없는 키를 지우는 것은 객체 저장소가 오류로 보지 않습니다.
     *
     * 둘을 따로 걸어 둡니다.
     * 한 번에 넘기면 앞엣것이 실패했을 때 뒤엣것이 아예 실행되지 않는데,
     * Redis 키가 남는 것과 사진이 남는 것 사이에는 아무 관계도 없습니다.
     * 실패했을 때 무엇이 남았는지가 로그에서 특정되는 것도 나눠 두어야 됩니다.
     */
    private void scheduleExternalCleanup(UUID accountId) {
        afterCommitExecutor.run(
                () -> recentPlaceStore.deleteAll(accountId),
                "탈퇴 뒤 최근 장소 삭제: accountId=" + accountId);

        afterCommitExecutor.run(
                () -> storageProvider.delete(storageProvider.profileImageKey(accountId)),
                "탈퇴 뒤 프로필 사진 삭제: accountId=" + accountId);
    }
}
