package com.pawtrail.user.infrastructure.provider.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * POST /v1/chat/completions 의 응답입니다.
 *
 * 필요한 것만 담습니다.
 * 실제 응답에는 사용량이나 식별자 같은 값이 더 오지만 우리가 쓰는 것은 문장 하나뿐입니다.
 *
 * 구조화 출력을 켜 두었으므로 content 에는 JSON 문자열이 들어옵니다.
 * 그 문자열을 다시 파싱해 summary 를 꺼내는 일은 구현이 합니다.
 *
 * 모르는 필드는 무시합니다.
 * 응답 형태가 늘어나도 우리 쪽이 깨지지 않습니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiChatResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content) {
    }
}
