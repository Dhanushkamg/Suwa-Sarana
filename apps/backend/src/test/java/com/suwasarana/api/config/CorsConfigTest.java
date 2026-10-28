package com.suwasarana.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {

    @Test
    @DisplayName("a comma-separated list yields every origin, trimmed")
    void multipleOrigins() {
        assertThat(CorsConfig.parseOrigins(" https://suwasarana.lk , https://www.suwasarana.lk,http://localhost:3000 "))
                .containsExactly("https://suwasarana.lk", "https://www.suwasarana.lk", "http://localhost:3000");
    }

    @Test
    @DisplayName("blank means no cross-origin access")
    void blank() {
        assertThat(CorsConfig.parseOrigins("")).isEmpty();
        assertThat(CorsConfig.parseOrigins(null)).isEmpty();
        assertThat(CorsConfig.parseOrigins(" , ")).isEmpty();
    }

    @Test
    @DisplayName("a wildcard is rejected because credentials are enabled")
    void wildcardRejected() {
        assertThatThrownBy(() -> CorsConfig.parseOrigins("*")).isInstanceOf(IllegalStateException.class).hasMessageContaining("wildcard");
        assertThatThrownBy(() -> CorsConfig.parseOrigins("https://a.example,https://*.example.com"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("wildcard");
    }

    @Test
    @DisplayName("a trailing slash is tolerated, a path is not")
    void shape() {
        assertThat(CorsConfig.parseOrigins("https://suwasarana.lk/")).containsExactly("https://suwasarana.lk");
        assertThatThrownBy(() -> CorsConfig.parseOrigins("https://suwasarana.lk/app")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CorsConfig.parseOrigins("suwasarana.lk")).isInstanceOf(IllegalStateException.class);
    }
}
