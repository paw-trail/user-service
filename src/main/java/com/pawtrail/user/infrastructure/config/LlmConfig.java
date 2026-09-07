package com.pawtrail.user.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 언어 모델 설정을 빈으로 올립니다.
 *
 * @ConfigurationProperties 만 붙여 둔 클래스는 저절로 빈이 되지 않습니다.
 * 어딘가에서 켜 주어야 하며, 객체 저장소 설정은 S3Config 가 그 일을 하고 있습니다.
 *
 * 이 클래스는 빈을 만들지 않습니다.
 * S3 는 클라이언트 두 개를 만들어야 해서 설정 클래스가 그것까지 맡지만,
 * 언어 모델은 호출하는 쪽이 빌더를 받아 직접 만들기 때문에 여기서 만들 것이 없습니다.
 *
 * 그래도 클래스를 따로 두는 이유가 있습니다.
 * 진입점에 스캔을 켜면 두 프로퍼티가 한꺼번에 잡혀 S3Config 의 등록이 중복이 되고,
 * 그 파일은 복제할 때 고칠 문자열을 줄이려고 스캔 범위를 일부러 비워 둔 자리입니다.
 * 쓰는 쪽에 붙이는 방법도 있으나 이 설정을 두 곳이 쓰고 있어
 * 그중 하나에만 등록이 붙는 모양이 됩니다.
 *
 * 나중에 언어 모델 전용 빈이 생기면 여기가 그 자리입니다.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {
}
