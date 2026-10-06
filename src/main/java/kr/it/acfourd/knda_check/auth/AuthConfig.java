package kr.it.acfourd.knda_check.auth;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AuthConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}