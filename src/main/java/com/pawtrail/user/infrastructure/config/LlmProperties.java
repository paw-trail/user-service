package com.pawtrail.user.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 하루 요약을 만드는 언어 모델 설정입니다.
 *
 * apiKey 만 성격이 다릅니다.
 * 나머지는 config 저장소에 값이 그대로 적혀 있지만 이 값은 거기에 자리표시자만 있고
 * 실제 값은 실행 구성이나 컨테이너 환경변수에서 옵니다.
 * config 는 공개 저장소라 키를 두면 팀원 전부가 보게 되고 깃 이력에도 남습니다.
 * RS256 개인키나 메일 앱 비밀번호와 같은 취급입니다.
 *
 * 객체 저장소가 키를 아예 안 받는 것과는 다릅니다.
 * 그쪽은 DefaultCredentialsProvider 가 환경변수를 직접 읽어 주지만
 * 여기는 Authorization 헤더를 우리가 만들어야 해서 값을 손에 쥐어야 합니다.
 *
 * 값에 검증을 붙였습니다.
 * 빠지면 기동이 실패해 누락이 바로 드러납니다.
 * 없는 채로 뜨면 첫 요약 요청에서야 알게 되는데 그때는 원인을 찾기가 훨씬 어렵습니다.
 *
 * 검증을 추가할 때는 세 곳을 함께 봐야 합니다.
 *   config 저장소의 user-service.yml
 *   src/test/resources/application.yml
 *   이 클래스
 * 테스트 yml 은 설정 서버를 끄므로 값이 하나도 안 내려오는데,
 * 여기에 검증이 있으면 contextLoads 가 그 자리에서 깨집니다.
 *
 * @param apiKey            OpenAI API 키입니다. 환경변수 OPENAI_API_KEY 에서 옵니다.
 * @param model             모델 이름입니다. 요청 본문에 그대로 실립니다.
 * @param timeoutSeconds    호출 제한 시간입니다.
 *                          전역 app.rest-client 의 5초로는 부족해 따로 둡니다.
 *                          그 값을 늘리면 장소나 판정 호출까지 느슨해집니다.
 * @param maxSummaryLength  요약문의 최대 길이입니다. 공백을 포함해 셉니다.
 *                          넘겨서 오면 저장하지 않고 실패로 봅니다.
 * @param cooldownSeconds   같은 날짜를 다시 만들 때까지 기다리는 시간입니다.
 * @param dailyLimit        계정당 하루에 만들 수 있는 횟수입니다.
 *                          실패한 호출도 셉니다. 남용을 막는 것이 목적이기 때문입니다.
 */
@Validated
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(

        @NotBlank(message = "OPENAI_API_KEY 환경변수가 필요합니다")
        String apiKey,

        @NotBlank(message = "app.llm.model 이 필요합니다")
        String model,

        @Positive(message = "app.llm.timeout-seconds 는 양수여야 합니다")
        long timeoutSeconds,

        @Positive(message = "app.llm.max-summary-length 는 양수여야 합니다")
        int maxSummaryLength,

        @Positive(message = "app.llm.cooldown-seconds 는 양수여야 합니다")
        long cooldownSeconds,

        @Positive(message = "app.llm.daily-limit 은 양수여야 합니다")
        int dailyLimit
) {
}
