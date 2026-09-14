package com.pawtrail.user.infrastructure.provider.internal;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.user.domain.exception.UserErrorCode;
import com.pawtrail.user.domain.provider.PetProvider;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 도메인이 선언한 약속을 pet 서비스 호출로 구현합니다.
 *
 * 앞서 만든 provider 셋과 같은 모양을 씁니다.
 * 빌더를 주입받아 baseUrl 만 붙이고, 응답 봉투는 벗기지 않습니다.
 * 우리가 보는 것이 상태 코드뿐이라 본문을 읽을 일이 없습니다.
 *
 * 실패 처리만 셋과 다릅니다.
 * 그쪽은 못 받아온 것을 삼키고 빈 값을 돌려주지만 이쪽은 예외를 던집니다.
 * 이유는 PetProvider 의 설명에 적어 두었습니다.
 */
@Slf4j
@Component
public class PetProviderImpl implements PetProvider {

    private static final String BASE_URL = "lb://pet-service";
    private final RestClient restClient;

    /**
     * 빌더를 주입받아 RestClient 를 만듭니다.
     *
     * @Qualifier 를 반드시 붙여야 합니다.
     * 같은 타입의 빈이 셋이고 그중 하나가 @Primary 입니다.
     * 빠뜨리면 아무것도 얹히지 않은 그 빌더가 조용히 주입되어
     * lb:// 를 풀지 못하고 기동이 아니라 호출하는 순간에 실패합니다.
     *
     * @RequiredArgsConstructor 를 쓰지 않는 것도 그 때문입니다.
     * 롬복이 만드는 생성자에는 @Qualifier 가 붙지 않습니다.
     *
     * 이 빌더가 붙여 주는 인증 헤더가 여기서는 특히 중요합니다.
     * pet 의 /internal 은 X-User-Id 가 없으면 401 을 냅니다.
     * 공통 모듈의 인터셉터가 SecurityContext 에서 원래 사용자를 꺼내 실어 보내므로,
     * 사용자 요청을 받아 도는 이 경로에서는 그 값이 그대로 따라갑니다.
     */
    public PetProviderImpl(
            @Qualifier("internalRestClientBuilder") RestClient.Builder builder) {

        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    /**
     * 그 반려동물이 부른 사람의 것인지 확인합니다.
     *
     * 404 만 거짓으로 봅니다.
     * pet 이 없는 것과 남의 것을 그 코드 하나로 돌려주기로 정해 두었습니다.
     *
     * 나머지는 전부 예외입니다. 401 도 여기에 들어갑니다.
     * 그것은 소유권 문제가 아니라 인증 헤더가 실려 나가지 않았다는 뜻이고,
     * 우리 쪽 배선이 어긋난 것이라 사용자에게 "네 반려동물이 아니다" 로 안내하면 안 됩니다.
     *
     * 응답 본문을 읽지 않습니다.
     * 성공이면 그 자체로 "내 것" 이 확인되고 담긴 값은 쓰지 않습니다.
     * toBodilessEntity 로 본문을 흘려보내 역직렬화를 아예 하지 않습니다.
     *
     * 잡는 순서가 중요합니다.
     * HttpClientErrorException.NotFound 가 HttpClientErrorException 의 하위 타입이므로
     * 좁은 것을 먼저 잡지 않으면 404 까지 장애로 넘어갑니다.
     *
     * 로그를 두 단계로 나눕니다.
     * 소유권에 안 맞는 것은 정상 흐름에서도 나올 수 있어 info 로 남기고,
     * 호출 실패는 알아채야 하는 일이라 warn 으로 남깁니다.
     */
    @Override
    public boolean isOwned(UUID petId) {
        try {
            restClient.get()
                    .uri("/internal/pets/{petId}", petId)
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (HttpClientErrorException.NotFound e) {
            log.info("반려동물이 없거나 이 사람의 것이 아닙니다: petId={}", petId);
            return false;

        } catch (Exception e) {
            log.warn("반려동물 소유권을 확인하지 못했습니다: petId={}, reason={}",
                    petId, e.getMessage());
            throw new CustomException(UserErrorCode.PET_UNAVAILABLE);
        }
    }
}
