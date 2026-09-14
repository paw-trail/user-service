package com.pawtrail.user.application.support;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.domain.exception.UserErrorCode;
import com.pawtrail.user.domain.provider.PetProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 요청이 담고 온 반려동물 식별자가 부른 사람의 것인지 확인합니다.
 *
 * 왜 따로 두는가
 *
 * 같은 검사가 네 자리에서 필요합니다.
 *   PATCH /users/me/default-pet     대표 반려동물 지정
 *   POST  /itineraries              일정에 담기
 *   PATCH /itineraries/{stopId}     일정 수정
 *   POST  /visits                   즉흥 방문 기록
 *
 * 검사가 한 줄이면 각자 하게 두었을 텐데 두 조각입니다.
 * 값이 없으면 부르지 않는 것과, 아니라고 나왔을 때 무엇으로 던질지가 함께 있습니다.
 * 네 곳에 복사해 두면 규칙이 바뀔 때 네 곳을 고쳐야 하고 그중 하나를 빠뜨립니다.
 *
 * provider 에 이 일을 맡기지 않은 이유
 *
 * domain/provider 는 무엇을 받아오는지만 적는 자리이고 다섯이 전부 값을 돌려줍니다.
 * 거기에 검사 메서드를 두면 혼자 값을 안 주고 예외로만 말하는 메서드가 됩니다.
 * 받아온 것을 무엇으로 해석할지는 부르는 쪽이 정한다는 것이 이 서비스의 축이기도 합니다.
 * 즐겨찾기에서 장소 실패는 목록을 죽이고 판정 실패는 배지만 비운 것이 그 예입니다.
 *
 * AfterCommitExecutor 와 같은 자리에 둡니다.
 * 한 도메인에 속하지 않고 여러 서비스가 같이 쓰는 application 계층의 도구입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetOwnershipValidator {

    private final PetProvider petProvider;

    /**
     * 소유권을 확인하고 아니면 막습니다.
     *
     * null 이면 아무것도 하지 않습니다.
     * 반려동물을 고르지 않은 것은 정상이며 네 자리에서 뜻이 모두 같습니다.
     *   대표 지정   대표를 해제하는 요청임
     *   일정과 방문  동반 동물 없이 담거나 기록하는 것이고 판정이 UNKNOWN 으로 나감
     * 판정을 부를 때 대표 반려동물이 없으면 호출을 건너뛰는 것과 같은 결입니다.
     *
     * 아니라고 나오면 PET_NOT_FOUND 404 를 던집니다.
     * pet 이 없는 것과 남의 것을 404 하나로 답하므로 우리도 그대로 전합니다.
     * 400 으로 내리면 우리가 모르는 것을 요청 형식 문제로 단정하는 것이 되고,
     * 403 으로 올리면 pet 이 감춘 존재 여부를 도로 드러내 식별자를 넣어 보며 캐낼 수 있습니다.
     *
     * 호출이 실패한 경우는 여기까지 오지 않습니다.
     * provider 가 PET_UNAVAILABLE 을 먼저 던집니다.
     * 둘을 가르는 이유는 사용자가 고칠 수 있는 실패와 기다려야 하는 실패가 다르기 때문입니다.
     *
     * @param petId 확인할 반려동물 식별자입니다. null 이면 검사하지 않습니다.
     */
    public void verify(UUID petId) {
        if (petId == null) {
            return;
        }

        if (!petProvider.isOwned(petId)) {
            log.info("소유권에 맞지 않는 반려동물을 요청했습니다: petId={}", petId);
            throw new CustomException(UserErrorCode.PET_NOT_FOUND);
        }
    }
}
