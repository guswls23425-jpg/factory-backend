package com.cafe.cafe_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SSE 연결이 MVC 요청 스레드를 점유하지 않도록 비동기 실행기를 설정한다.
 *
 * Spring MVC는 SseEmitter를 반환하면 해당 요청을 비동기 처리로 전환한다.
 * 이때 별도 스레드 풀(sseTaskExecutor)을 사용해 기본 요청 스레드 풀이
 * SSE 연결로 고갈되는 것을 방지한다.
 */
@Configuration
public class AsyncConfig implements WebMvcConfigurer {

    @Bean
    public AsyncTaskExecutor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);      // 항상 유지할 스레드 수
        executor.setMaxPoolSize(50);       // 최대 동시 SSE 연결 수
        executor.setQueueCapacity(100);    // 대기 큐
        executor.setThreadNamePrefix("sse-");
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(sseTaskExecutor());
        configurer.setDefaultTimeout(0L); // SSE는 서버가 명시적으로 끊을 때까지 유지
    }
}
