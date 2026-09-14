package com.pawtrail.user.application.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.domain.exception.UserErrorCode;
import com.pawtrail.user.domain.provider.PetProvider;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 반려동물 소유권 검사의 규칙을 봅니다.
 *
 * 네 자리가 이 클래스 하나를 부르므로 검사도 여기 모읍니다.
 * 각 서비스 테스트는 이것을 목으로 두고 자기 규칙만 봅니다.
 *
 * 실물 검증으로 가리기 어려운 자리가 둘 있습니다.
 *
 *   호출을 건너뛰는가   값이 없을 때 pet 을 아예 부르지 않는지는
 *                    응답만 봐서는 드러나지 않음
 *   실패 두 가지      남의 반려동물과 pet 장애가 서로 다른 코드로 갈리는지.
 *                    실물로 보려면 pet 을 일부러 내려야 함
 */
@ExtendWith(MockitoExtension.class)
class PetOwnershipValidatorTest {

    @Mock
    private PetProvider petProvider;

    @InjectMocks
    private PetOwnershipValidator petOwnershipValidator;

    private static final UUID PET_ID = UUID.fromString("11111111-0000-7000-8000-000000000001");

    @Test
    @DisplayName("내 반려동물이면 통과한다")
    void 통과() {
        when(petProvider.isOwned(PET_ID)).thenReturn(true);

        assertThatCode(() -> petOwnershipValidator.verify(PET_ID))
                .doesNotThrowAnyException();

        verify(petProvider).isOwned(PET_ID);
    }

    @Test
    @DisplayName("없거나 남의 반려동물이면 PET_NOT_FOUND 로 막는다")
    void 남의_것() {
        when(petProvider.isOwned(PET_ID)).thenReturn(false);

        assertThatThrownBy(() -> petOwnershipValidator.verify(PET_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.PET_NOT_FOUND);
    }

    @Test
    @DisplayName("값이 없으면 pet 을 아예 부르지 않는다")
    void 값이_없으면_호출하지_않음() {
        assertThatCode(() -> petOwnershipValidator.verify(null))
                .doesNotThrowAnyException();

        verify(petProvider, never()).isOwned(any());
    }

    @Test
    @DisplayName("호출이 실패하면 그 예외가 그대로 나가 PET_NOT_FOUND 로 바뀌지 않는다")
    void 호출_실패는_다른_코드() {
        when(petProvider.isOwned(PET_ID))
                .thenThrow(new CustomException(UserErrorCode.PET_UNAVAILABLE));

        assertThatThrownBy(() -> petOwnershipValidator.verify(PET_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(UserErrorCode.PET_UNAVAILABLE);
    }
}
