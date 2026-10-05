package security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "shop.jwt")
public class JwtProperties {

    /** HMAC-SHA256 密钥，长度必须 >= 32 字节 */
    private String secret = "shop-cloud-super-secret-key-please-change-in-prod-32bytes";

    private Duration accessTokenTtl = Duration.ofMinutes(30);
    private Duration refreshTokenTtl = Duration.ofDays(7);
}


