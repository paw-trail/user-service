package com.pawtrail.user.infrastructure.provider.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawtrail.user.domain.provider.LlmProvider;
import com.pawtrail.user.domain.provider.dto.ReviewData;
import com.pawtrail.user.domain.provider.dto.SummaryData;
import com.pawtrail.user.infrastructure.config.LlmProperties;
import com.pawtrail.user.infrastructure.provider.external.dto.OpenAiChatRequest;
import com.pawtrail.user.infrastructure.provider.external.dto.OpenAiChatResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 도메인이 선언한 약속을 OpenAI 호출로 구현합니다.
 *
 * external 아래에 둡니다.
 * 우리가 만든 다른 서비스가 아니라 바깥 시스템이며 S3StorageProvider 와 같은 자리입니다.
 *
 * 공식 SDK 를 쓰지 않습니다.
 * 우리가 하는 일이 요청 한 번에 문장 하나를 받는 것뿐이라 SDK 기능 대부분이 안 쓰이고,
 * SDK 는 자기 HTTP 클라이언트를 써서 공통 모듈이 세워 둔 배선을 아예 타지 않습니다.
 * 그러면 추적 식별자가 이 호출에만 안 붙어 장애를 쫓을 때 로그가 여기서 끊깁니다.
 *
 * S3 가 AWS SDK 를 쓰는 것은 서명 계산이 필요해서입니다.
 * 여기는 Authorization 헤더 하나라 그 이유가 없습니다.
 */
@Slf4j
@Component
public class LlmProviderImpl implements LlmProvider {

    private static final String BASE_URL = "https://api.openai.com/v1";

    /**
     * 모델에게 주는 규칙입니다.
     *
     * 설정 파일이 아니라 코드에 둡니다.
     * 설정 저장소는 공개라 우리가 모델에게 무엇을 시키는지가 드러나고,
     * 여러 줄 문자열은 들여쓰기 사고가 잦습니다.
     *
     * 무엇보다 이 문장을 고치면 출력이 바뀝니다.
     * 리뷰와 검증을 거쳐 배포되는 편이 맞고, 재배포가 필요한 것이 단점이 아니라 안전장치입니다.
     *
     * 문장 수를 정하지 않았습니다.
     * 정하면 그것이 내용을 자르는 기준이 되어, 여러 곳을 다녀온 날에
     * "여덟 곳을 다녀왔어요" 로 뭉개지고 정작 어디였는지가 사라집니다.
     * 길이 상한 하나만 두고 담을 만큼 담게 합니다.
     */
    private static final String SYSTEM_PROMPT = """
            너는 반려동물과 함께한 하루의 나들이 기록을 요약한다.

            입력으로 그날 다녀온 장소들과 시각, 사용자가 남긴 메모,
            후기가 있으면 그 본문과 별점이 주어진다.

            규칙
            - 공백을 포함해 200자를 넘기지 않는다.
            - "~했어요" 로 끝나는 부드러운 존댓말로 통일한다.
            - 입력에 없는 사실을 지어내지 않는다.
              날씨, 기분, 동행한 사람, 장소의 특징을 임의로 덧붙이지 않는다.
            - 후기가 없으면 장소와 시각만으로 쓴다. 후기가 없다는 말을 하지 않는다.
            - 별점은 후기가 있을 때만 언급한다.
            - 반려동물을 부를 때 이름을 지어내지 않는다. 견종이 주어지면 그것을 쓴다.
            """;

    private final RestClient restClient;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 빌더를 주입받아 RestClient 를 만듭니다.
     *
     * 아무것도 얹히지 않은 맨 빌더를 씁니다.
     * 다른 provider 가 쓰는 빌더는 lb:// 를 풀고 우리 인증 헤더를 싣는데,
     * 바깥 시스템에 그 헤더를 보낼 이유가 없고 주소도 실제 도메인입니다.
     *
     * @Qualifier 를 명시적으로 붙입니다.
     * 이 이름의 빈이 @Primary 라 안 붙여도 주입은 되지만,
     * 앞선 이슈에서 이것을 빠뜨려 엉뚱한 빌더가 조용히 들어간 적이 있습니다.
     * 이름을 적어 두면 나중에 @Primary 가 옮겨 가도 이 코드는 안 흔들립니다.
     *
     * 시간 제한을 여기서 따로 겁니다.
     * 공통 설정의 읽기 제한이 5초인데 언어 모델에게는 부족하고,
     * 그 값을 늘리면 장소나 판정 호출까지 함께 느슨해집니다.
     */
    public LlmProviderImpl(@Qualifier("defaultRestClientBuilder") RestClient.Builder builder,
                           LlmProperties properties,
                           ObjectMapper objectMapper) {

        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .requestFactory(timeoutFactory(properties.timeoutSeconds()))
                .build();
    }

    /**
     * 하루치 재료로 요약 문장을 만듭니다.
     *
     * 잡는 범위를 Exception 으로 둡니다.
     * 연결 거부, 시간 초과, 인증 실패, 응답 형태가 다른 것까지 결과가 모두 같습니다.
     * 문장을 못 받았다는 것 하나이고 부르는 쪽이 할 일도 하나입니다.
     *
     * 로그를 warn 으로 남깁니다.
     * 이 실패는 사용자에게 오류로 보이므로 조용히 사라지지는 않지만,
     * 원인이 우리 쪽인지 바깥인지는 로그가 없으면 알 수 없습니다.
     */
    @Override
    public String summarize(SummaryData data) {
        try {
            OpenAiChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(buildRequest(data))
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            return extractSummary(response);

        } catch (Exception e) {
            log.warn("요약을 만들지 못했습니다: visitDate={}, reason={}",
                    data.visitDate(), e.getMessage());
            return null;
        }
    }

    /**
     * 요청 본문을 만듭니다.
     *
     * 구조화 출력을 켭니다.
     * 이 값이 화면에 그대로 나가는 문자열이라 서두나 굵은 글씨 표시가 섞이면 그대로 보입니다.
     * 프롬프트로 금지해도 확률을 낮출 뿐이지만 스키마 밖이면 물리적으로 안 섞입니다.
     *
     * strict 와 additionalProperties 를 함께 둡니다.
     * 앞엣것은 스키마를 반드시 지키게 하고 뒤엣것은 없는 칸을 만들지 못하게 합니다.
     */
    private OpenAiChatRequest buildRequest(SummaryData data) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("summary", Map.of("type", "string")));
        schema.put("required", List.of("summary"));
        schema.put("additionalProperties", false);

        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", "daily_summary");
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", schema);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);

        return new OpenAiChatRequest(
                properties.model(),
                properties.reasoningEffort(),
                List.of(new OpenAiChatRequest.Message("system", SYSTEM_PROMPT),
                        new OpenAiChatRequest.Message("user", toUserMessage(data))),
                responseFormat);
    }

    /**
     * 그날 재료를 모델이 읽을 형태로 만듭니다.
     *
     * JSON 으로 넘깁니다.
     * 문장으로 풀어 쓰면 그 문장이 이미 요약이라 모델이 그대로 베낄 여지가 생기고,
     * 값이 비었을 때 "없음" 같은 말을 넣게 되어 그것이 결과에 새어 나옵니다.
     *
     * 직렬화가 실패하면 예외가 나가고 부르는 메서드가 잡습니다.
     * 여기서 따로 다루지 않는 것은 결과가 같기 때문입니다.
     */
    private String toUserMessage(SummaryData data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("visitDate", data.visitDate().toString());
            payload.put("visits", data.visits());
            payload.put("reviews", toReviewPayload(data.reviews()));
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("요약 재료를 만들지 못했습니다", e);
        }
    }

    /**
     * 후기에서 식별자를 떼어 냅니다.
     *
     * 모델에게 UUID 를 넘겨 봐야 문장에 쓸 수 없고 토큰만 먹습니다.
     * 장소 식별자도 마찬가지인데, 어느 장소의 후기인지는
     * 방문 목록의 순서와 이름으로 이미 드러납니다.
     */
    private List<Map<String, Object>> toReviewPayload(List<ReviewData> reviews) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (ReviewData review : reviews) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("rating", review.rating());
            one.put("content", review.content());
            one.put("petBreed", review.petBreedAtVisit());
            result.add(one);
        }
        return result;
    }

    /**
     * 응답에서 문장을 꺼냅니다.
     *
     * 구조화 출력이라 content 안에 JSON 문자열이 들어 있어 한 번 더 파싱합니다.
     *
     * 길이를 여기서 봅니다.
     * 정해진 길이를 넘겼다는 것은 모델이 지시를 흘렸다는 뜻이라 그 결과를 넘길 이유가 없습니다.
     * 잘라서 돌려주지 않는 것은 끊긴 문장이 화면에 그대로 남기 때문입니다.
     */
    private String extractSummary(OpenAiChatResponse response) throws Exception {
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            log.warn("요약 응답이 비어 있습니다");
            return null;
        }

        String content = response.choices().get(0).message().content();
        if (content == null || content.isBlank()) {
            log.warn("요약 응답에 내용이 없습니다");
            return null;
        }

        JsonNode node = objectMapper.readTree(content);
        JsonNode summary = node.get("summary");

        if (summary == null || summary.asText().isBlank()) {
            log.warn("요약 응답에서 문장을 찾지 못했습니다");
            return null;
        }

        String text = summary.asText().trim();
        if (text.length() > properties.maxSummaryLength()) {
            log.warn("요약이 너무 깁니다: {}자, 상한 {}자",
                    text.length(), properties.maxSummaryLength());
            return null;
        }

        return text;
    }

    /**
     * 이 호출에만 걸리는 시간 제한을 만듭니다.
     *
     * 연결과 읽기에 같은 값을 겁니다.
     * 연결이 안 되는 것은 대개 즉시 드러나고, 오래 걸리는 것은 언제나 읽기 쪽입니다.
     */
    private SimpleClientHttpRequestFactory timeoutFactory(long seconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(seconds));
        factory.setReadTimeout(Duration.ofSeconds(seconds));
        return factory;
    }
}
