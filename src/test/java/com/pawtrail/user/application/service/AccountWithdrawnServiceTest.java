package com.pawtrail.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 탈퇴 처리의 규칙을 검사합니다.
 *
 * 스프링 컨텍스트도 데이터베이스도 Redis 도 띄우지 않습니다.
 * 여기서 보는 것은 프로필 상태에 따라 무엇이 갈리고 무엇이 갈리지 않는가입니다.
 *
 * AfterCommitExecutor 만 목이 아니라 실제 객체를 넣습니다.
 * 그 클래스는 트랜잭션이 없으면 넘겨받은 일을 그 자리에서 실행하므로,
 * 실제 객체를 쓰면 Redis 와 객체 저장소가 실제로 불렸는지까지 그대로 확인됩니다.
 * 목으로 두면 등록이 몇 번 됐는지만 보이고 그 안에서 무엇을 하는지는 보이지 않습니다.
 *
 * 감시 객체라 호출 횟수도 함께 셀 수 있어, 둘을 따로 등록했는지도 여기서 봅니다.
 */
@ExtendWith(MockitoExtension.class)
class AccountWithdrawnServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private VisitLogRepository visitLogRepository;

    @Mock
    private ItineraryStopRepository itineraryStopRepository;

    @Mock
    private DailySummaryRepository dailySummaryRepository;

    @Mock
    private RecentPlaceStore recentPlaceStore;

    @Mock
    private StorageProvider storageProvider;

    @Mock
    private AuditorProvider auditorProvider;

    @Spy
    private AfterCommitExecutor afterCommitExecutor = new AfterCommitExecutor();

    @InjectMocks
    private AccountWithdrawnService accountWithdrawnService;

    private static final UUID ACCOUNT_ID = UUID.fromString("01a0726a-64e9-7712-9ecb-4996e2dcd75f");
    private static final String IMAGE_KEY = "users/01a0726a-64e9-7712-9ecb-4996e2dcd75f/profile";

    // 이벤트 소비 경로라 보안 컨텍스트가 비어 있고, 감사 주체가 시스템 이름을 돌려줌
    private static final String SYSTEM = "SYSTEM";

    @BeforeEach
    void setUp() {
        when(auditorProvider.current()).thenReturn(SYSTEM);
    }

    // ── 프로필 처리 — 세 갈래 ────────────────────────────────────

    @Test
    @DisplayName("정상이면 닉네임을 치환하고 사진을 비우고 삭제 표시를 남긴다")
    void 정상_익명화() {
        UserProfile profile = givenLiveProfile();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        assertThat(profile.getNickname()).isEqualTo("탈퇴한 사용자");
        assertThat(profile.getProfileImageUrl()).isNull();
        assertThat(profile.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("정상 경로에서는 저장을 부르지 않는다")
    void 정상은_저장을_안_부름() {
        givenLiveProfile();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        // 조회해 온 엔티티라 변경 감지가 커밋 시점에 UPDATE 를 냄
        // 여기서 save 를 부르면 같은 일을 두 번 시키는 셈임
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 탈퇴 표시가 있으면 프로필을 건드리지 않는다")
    void 이미_탈퇴_표시() {
        UserProfile profile = UserProfile.create(ACCOUNT_ID, "다정이네");
        profile.delete("먼저");
        givenProfile(profile);
        givenStorageKey();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        // 다시 익명화하면 닉네임이 덮이고 삭제자도 흔들림
        assertThat(profile.getNickname()).isEqualTo("다정이네");
        assertThat(profile.getDeletedBy()).isEqualTo("먼저");
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("행이 아예 없으면 탈퇴 표시 행을 만든다")
    void 표시_행_생성() {
        givenNoProfile();
        givenStorageKey();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        UserProfile saved = captureSaved();
        assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("표시 행은 닉네임 없이 삭제 표시만 갖는다")
    void 표시_행은_닉네임이_없음() {
        givenNoProfile();
        givenStorageKey();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        // 치환의 목적이 auth 가 끊은 신원을 이쪽에서도 지우는 것인데
        // 이 행은 애초에 닉네임을 가진 적이 없어 지울 신원이 없음
        assertThat(captureSaved().getNickname()).isNull();
    }

    @Test
    @DisplayName("삭제자로 감사 주체가 돌려준 값을 쓴다")
    void 삭제자() {
        UserProfile profile = givenLiveProfile();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        // 같은 행의 updated_by 도 같은 판정을 거쳐 채워지므로 값이 갈리지 않음
        assertThat(profile.getDeletedBy()).isEqualTo(SYSTEM);
    }

    // ── 정리 — 갈래와 무관하게 항상 ──────────────────────────────

    @Test
    @DisplayName("정상이면 표 넷과 외부 저장소를 지운다")
    void 정상_정리() {
        givenLiveProfile();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        verifyAllRowsDeleted();
        verify(recentPlaceStore).deleteAll(ACCOUNT_ID);
        verify(storageProvider).delete(IMAGE_KEY);
    }

    @Test
    @DisplayName("이미 탈퇴 표시여도 정리는 그대로 실행된다")
    void 이미_탈퇴_표시여도_정리() {
        UserProfile profile = UserProfile.create(ACCOUNT_ID, "다정이네");
        profile.delete("먼저");
        givenProfile(profile);
        givenStorageKey();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        verifyAllRowsDeleted();
        verify(recentPlaceStore).deleteAll(ACCOUNT_ID);
        verify(storageProvider).delete(IMAGE_KEY);
    }

    @Test
    @DisplayName("행이 없어도 정리는 그대로 실행된다")
    void 행이_없어도_정리() {
        givenNoProfile();
        givenStorageKey();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        // 쓰기 경로가 프로필을 안 보므로 프로필 없이도 다른 표에 행이 있을 수 있음
        // 여기서 안 지우면 지울 기회가 다시 오지 않음
        verifyAllRowsDeleted();
        verify(recentPlaceStore).deleteAll(ACCOUNT_ID);
        verify(storageProvider).delete(IMAGE_KEY);
    }

    @Test
    @DisplayName("사진 자리는 프로필이 아니라 계정 식별자로 정한다")
    void 사진_자리() {
        givenNoProfile();
        givenStorageKey();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        // 프로필이 없는 갈래에서도 지울 자리를 알아야 하므로 계산해서 씀
        verify(storageProvider).profileImageKey(ACCOUNT_ID);
    }

    // ── 순서와 등록 방식 ────────────────────────────────────────

    @Test
    @DisplayName("프로필을 먼저 다루고 그다음 표를 지운다")
    void 순서() {
        UserProfile profile = givenLiveProfile();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        // 벌크 삭제가 영속성 컨텍스트를 우회하므로 순서가 보이는 대로 나가야 함
        InOrder order = inOrder(userProfileRepository, favoriteRepository);
        order.verify(userProfileRepository).findByIdIncludingDeleted(ACCOUNT_ID);
        order.verify(favoriteRepository).deleteAllByAccountId(ACCOUNT_ID);
        assertThat(profile.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("최근 장소와 사진을 따로 등록한다")
    void 따로_등록() {
        givenLiveProfile();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        // 한 번에 넘기면 앞엣것이 실패했을 때 뒤엣것이 아예 실행되지 않고,
        // 무엇이 남았는지도 설명 한 줄로 뭉쳐 로그만 보고는 가릴 수 없음
        ArgumentCaptor<String> descriptions = ArgumentCaptor.forClass(String.class);
        verify(afterCommitExecutor, times(2)).run(any(), descriptions.capture());
        assertThat(descriptions.getAllValues()).doesNotHaveDuplicates();
    }

    // ── 그 밖 ──────────────────────────────────────────────────

    @Test
    @DisplayName("담아 둔 것이 하나도 없어도 정상으로 끝난다")
    void 지울_것이_없어도() {
        givenLiveProfile();

        accountWithdrawnService.withdraw(ACCOUNT_ID);

        // 벌크 삭제는 지운 행 수를 돌려주며 0 이어도 실패가 아님
        verifyAllRowsDeleted();
    }

    @Test
    @DisplayName("계정 식별자가 없으면 표시 행을 만들지 않고 거부한다")
    void 식별자_없음() {
        when(userProfileRepository.findByIdIncludingDeleted(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountWithdrawnService.withdraw(null))
                .isInstanceOf(IllegalArgumentException.class);

        // 던져서 나가야 재시도와 DLQ 가 받아 줌, 여기서 삼키면 조용히 사라짐
        verify(userProfileRepository, never()).save(any());
        verify(favoriteRepository, never()).deleteAllByAccountId(any());
    }

    // ── 도우미 ──────────────────────────────────────────────────

    private UserProfile givenLiveProfile() {
        UserProfile profile = UserProfile.create(ACCOUNT_ID, "다정이네");
        profile.changeProfileImageUrl(IMAGE_KEY);
        givenProfile(profile);
        givenStorageKey();
        return profile;
    }

    private void givenProfile(UserProfile profile) {
        when(userProfileRepository.findByIdIncludingDeleted(ACCOUNT_ID))
                .thenReturn(Optional.of(profile));
    }

    private void givenNoProfile() {
        when(userProfileRepository.findByIdIncludingDeleted(ACCOUNT_ID))
                .thenReturn(Optional.empty());
    }

    private void givenStorageKey() {
        when(storageProvider.profileImageKey(ACCOUNT_ID)).thenReturn(IMAGE_KEY);
    }

    private UserProfile captureSaved() {
        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        return captor.getValue();
    }

    private void verifyAllRowsDeleted() {
        verify(favoriteRepository).deleteAllByAccountId(ACCOUNT_ID);
        verify(visitLogRepository).deleteAllByAccountId(ACCOUNT_ID);
        verify(itineraryStopRepository).deleteAllByAccountId(ACCOUNT_ID);
        verify(dailySummaryRepository).deleteAllByAccountId(ACCOUNT_ID);
    }
}
