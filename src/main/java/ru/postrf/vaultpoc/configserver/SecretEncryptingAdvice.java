package ru.postrf.vaultpoc.configserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ControllerAdvice
@ConditionalOnProperty(name = "config.server.secret-encryption.enabled",
        havingValue = "true", matchIfMissing = true)
public class SecretEncryptingAdvice implements ResponseBodyAdvice<Environment> {

    private static final Logger log = LoggerFactory.getLogger(SecretEncryptingAdvice.class);

    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile(".*password.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*secret.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\.key$", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*token", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*credential", Pattern.CASE_INSENSITIVE)
    );

    @Autowired(required = false)
    private TextEncryptorLocator encryptorLocator;

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        boolean supported = Environment.class.isAssignableFrom(returnType.getParameterType());
        log.debug("supports() called: returnType={}, supported={}",
                returnType.getParameterType().getSimpleName(), supported);
        return supported;
    }

    @Override
    public Environment beforeBodyWrite(Environment body,
                                       MethodParameter returnType,
                                       MediaType selectedContentType,
                                       Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                       ServerHttpRequest request,
                                       ServerHttpResponse response) {
        if (body == null) {
            log.warn("beforeBodyWrite: body is null");
            return null;
        }

        log.info("beforeBodyWrite: {} property sources to process", body.getPropertySources().size());

        if (encryptorLocator == null) {
            log.warn("TextEncryptorLocator not available — skipping encryption");
            return body;
        }

        int encryptedCount = 0;
        for (PropertySource ps : body.getPropertySources()) {
            Map<String, String> source = (Map<String, String>) (Map) ps.getSource();
            log.debug("Processing PropertySource: {} ({} entries)", ps.getName(), source.size());

            for (Map.Entry<String, String> entry : source.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value == null || value.startsWith("{cipher}")) {
                    continue;
                }
                if (isSensitive(key)) {
                    try {
                        org.springframework.security.crypto.encrypt.TextEncryptor textEncryptor =
                                encryptorLocator.locate(source);
                        String encrypted = textEncryptor.encrypt(value);
                        entry.setValue("{cipher}" + encrypted);
                        encryptedCount++;
                        log.info("Encrypted property: {} -> {cipher}...", key);
                    } catch (Exception e) {
                        log.error("Failed to encrypt property: {}", key, e);
                    }
                }
            }
        }

        log.info("Encrypted {} properties in total", encryptedCount);
        return body;
    }

    private static boolean isSensitive(String key) {
        return SECRET_PATTERNS.stream().anyMatch(p -> p.matcher(key).matches());
    }
}
