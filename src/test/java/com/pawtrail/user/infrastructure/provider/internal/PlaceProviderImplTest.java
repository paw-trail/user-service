package com.pawtrail.user.infrastructure.provider.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.pawtrail.user.domain.provider.dto.PlaceData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 장소를 나눠 부르는 규칙을 봅니다.
 *
 * 이 레포의 첫 provider 테스트입니다.
 * 그동안은 서비스 테스트가 provider 를 목으로 두었는데,
 * 나눠 부르는 일이 provider 안에서 일어나 목으로는 보이지 않습니다.
 *
 * MockRestServiceServer 로 HTTP 를 흉내 냅니다.
 * spring-boot-starter-test 에 이미 들어 있어 의존성이 늘지 않습니다.
 *
 * 실물 검증으로 가리기 어려운 자리를 봅니다.
 *
 *   묶음 수      101개가 정말 두 번으로 나가는지는 응답만 봐서는 드러나지 않음
 *   중간 실패    두 번째 묶음만 실패시키려면 실물에서는 재현이 어려움
 *   경계        100 과 101 의 차이
 */
class PlaceProviderImplTest {

    private MockRestServiceServer server;
    private PlaceProviderImpl placeProvider;

    private static final String URL_PREFIX = "lb://place-service/internal/places?ids=";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        placeProvider = new PlaceProviderImpl(builder);
    }

    @Test
    @DisplayName("100개까지는 한 번만 부른다")
    void 경계_100() {
        List<UUID> ids = ids(100);

        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withSuccess(body(ids), MediaType.APPLICATION_JSON));

        Map<UUID, PlaceData> result = placeProvider.findByIds(ids);

        server.verify();
        assertThat(result).hasSize(100);
    }

    @Test
    @DisplayName("101개면 100 + 1 로 두 번 부른다")
    void 경계_101() {
        List<UUID> ids = ids(101);

        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withSuccess(body(ids.subList(0, 100)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withSuccess(body(ids.subList(100, 101)), MediaType.APPLICATION_JSON));

        Map<UUID, PlaceData> result = placeProvider.findByIds(ids);

        server.verify();
        assertThat(result).hasSize(101);
        assertThat(result).containsKeys(ids.get(0), ids.get(100));
    }

    @Test
    @DisplayName("250개면 100 + 100 + 50 으로 세 번 부른다")
    void 여러_묶음() {
        List<UUID> ids = ids(250);

        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withSuccess(body(ids.subList(0, 100)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withSuccess(body(ids.subList(100, 200)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withSuccess(body(ids.subList(200, 250)), MediaType.APPLICATION_JSON));

        Map<UUID, PlaceData> result = placeProvider.findByIds(ids);

        server.verify();
        assertThat(result).hasSize(250);
    }

    @Test
    @DisplayName("두 번째 묶음이 실패하면 전체를 포기하고 null 을 돌려준다")
    void 중간_실패() {
        List<UUID> ids = ids(150);

        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withSuccess(body(ids.subList(0, 100)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withServerError());

        Map<UUID, PlaceData> result = placeProvider.findByIds(ids);

        server.verify();
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("첫 묶음이 실패하면 두 번째를 아예 부르지 않는다")
    void 첫_묶음_실패() {
        List<UUID> ids = ids(150);

        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withServerError());

        Map<UUID, PlaceData> result = placeProvider.findByIds(ids);

        // 기대한 요청이 하나뿐인데 두 번 나갔다면 여기서 걸림
        server.verify();
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("어느 묶음에 없는 장소는 결과에서 빠지고 나머지는 그대로 온다")
    void 일부_누락() {
        List<UUID> ids = ids(150);

        // 첫 묶음에서 앞의 두 건이 빠진 응답
        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withSuccess(body(ids.subList(2, 100)), MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith(URL_PREFIX)))
                .andRespond(withSuccess(body(ids.subList(100, 150)), MediaType.APPLICATION_JSON));

        Map<UUID, PlaceData> result = placeProvider.findByIds(ids);

        server.verify();
        assertThat(result).hasSize(148);
        assertThat(result).doesNotContainKeys(ids.get(0), ids.get(1));
    }

    @Test
    @DisplayName("빈 목록이면 부르지 않고 빈 Map 을 돌려준다")
    void 빈_목록() {
        Map<UUID, PlaceData> result = placeProvider.findByIds(List.of());

        server.verify();
        assertThat(result).isEmpty();
    }

    /**
     * 순서가 정해진 식별자를 n 개 만듭니다.
     *
     * 무작위 UUID 를 쓰면 실패했을 때 어느 건인지 읽기 어렵습니다.
     * 마지막 열두 자리에 일련번호를 넣어 눈으로 가릴 수 있게 합니다.
     */
    private List<UUID> ids(int n) {
        List<UUID> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(UUID.fromString(String.format("aaaaaaaa-0000-7000-8000-%012d", i)));
        }
        return result;
    }

    /**
     * 공통 응답 봉투에 담은 장소 목록 JSON 을 만듭니다.
     *
     * 필드는 PlaceResponse 가 읽는 것만 채웁니다.
     * @JsonIgnoreProperties(ignoreUnknown = true) 가 붙어 있어 나머지는 없어도 됩니다.
     */
    private String body(List<UUID> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"code\":\"SUCCESS\",\"message\":\"ok\",\"data\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"placeId\":\"").append(ids.get(i)).append("\",")
              .append("\"name\":\"장소").append(i).append("\",")
              .append("\"placeType\":\"CAFE\",")
              .append("\"imageUrl\":null,")
              .append("\"lat\":37.5,\"lon\":127.0,")
              .append("\"supplyPoint\":false}");
        }
        sb.append("],\"traceId\":\"test\"}");
        return sb.toString();
    }
}
