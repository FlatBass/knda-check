package kr.it.acfourd.knda_check.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 관리자 계정. 비밀번호 원문이 아니라 BCrypt 해시만 받는다. 값이 없거나 형식이 틀리면 앱이 시작되지 않는다. */
@Validated
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        @NotBlank String username,
        @NotBlank @Pattern(regexp = "^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$", message = "app.admin.password-hash 는 BCrypt 해시여야 합니다") String passwordHash) {
}