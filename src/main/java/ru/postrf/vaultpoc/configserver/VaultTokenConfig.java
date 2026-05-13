package ru.postrf.vaultpoc.configserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.config.server.environment.ConfigTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a static Vault token to Spring Cloud Config Server's Vault backend.
 *
 * <p>Background: in composite mode the {@code composite[].vault.token} property is
 * declared on {@code VaultEnvironmentProperties} but is never consumed at runtime.
 * Both the legacy {@code VaultEnvironmentRepository} and the newer
 * {@code SpringVaultEnvironmentRepository} (activated by {@code authentication: TOKEN})
 * obtain the token exclusively via a {@link ConfigTokenProvider} bean. The default
 * provider auto-registered by {@code ConfigServerAutoConfiguration} is
 * {@code HttpRequestConfigTokenProvider}, which expects an {@code X-Config-Token}
 * HTTP header on every incoming request and throws
 * {@code IllegalArgumentException: Missing required header in HttpServletRequest:
 * X-Config-Token} when it is absent.
 *
 * <p>This bean replaces that default (the auto-config bean is annotated with
 * {@code @ConditionalOnMissingBean(ConfigTokenProvider.class)}) and returns a fixed
 * token sourced from {@code VAULT_TOKEN} env var, so clients never need to forward
 * a Vault token themselves.
 *
 * <p>Production note: a static, server-side token is acceptable for this PoC only.
 * In production, switch to AppRole / Kubernetes auth and remove this bean.
 */
@Configuration
public class VaultTokenConfig {

    @Bean
    public ConfigTokenProvider vaultStaticTokenProvider(
            @Value("${VAULT_TOKEN:root-token-poc}") String token) {
        return () -> token;
    }
}
