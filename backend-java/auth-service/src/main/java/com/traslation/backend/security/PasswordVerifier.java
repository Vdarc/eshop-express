package com.traslation.backend.security;

import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 兼容 werkzeug 与 BCrypt 两类密码哈希。
 *
 * <p>现存数据有三种格式：
 *
 * <ul>
 *   <li>{@code $2a$/$2b$/$2y$...} —— BCrypt（早期账号，Java 侧新注册也用这个）
 *   <li>{@code scrypt:32768:8:1$salt$hex} —— werkzeug 2.3+ 默认
 *   <li>{@code pbkdf2:sha256:600000$salt$hex} —— werkzeug 旧版默认
 * </ul>
 *
 * <p>只认 BCrypt 会让早期注册的账号全部无法登录，属于必须处理的兼容问题。
 */
@Component
public class PasswordVerifier {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /** 校验明文密码与库中哈希是否匹配；无法识别的格式一律返回 false。 */
    public boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null || stored.isBlank()) return false;

        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"))
            return bcrypt.matches(rawPassword, stored);

        if (stored.startsWith("scrypt:")) return matchScrypt(rawPassword, stored);
        if (stored.startsWith("pbkdf2:")) return matchPbkdf2(rawPassword, stored);

        return false;
    }

    /** 格式：scrypt:{n}:{r}:{p}${salt}${hexDigest} */
    private boolean matchScrypt(String raw, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 3) return false;
        String[] params = parts[0].split(":");
        if (params.length != 4) return false;
        try {
            int n = Integer.parseInt(params[1]);
            int r = Integer.parseInt(params[2]);
            int p = Integer.parseInt(params[3]);
            byte[] salt = parts[1].getBytes(StandardCharsets.UTF_8);
            byte[] expected = HexFormat.of().parseHex(parts[2]);
            byte[] actual =
                    SCrypt.generate(
                            raw.getBytes(StandardCharsets.UTF_8), salt, n, r, p, expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 格式：pbkdf2:{digest}:{iterations}${salt}${hexDigest} */
    private boolean matchPbkdf2(String raw, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 3) return false;
        String[] params = parts[0].split(":");
        if (params.length != 3) return false;
        try {
            // werkzeug 的 digest 名形如 sha256 / sha1 / sha512
            String jdkAlg =
                    switch (params[1].toLowerCase()) {
                        case "sha256" -> "PBKDF2WithHmacSHA256";
                        case "sha512" -> "PBKDF2WithHmacSHA512";
                        case "sha1" -> "PBKDF2WithHmacSHA1";
                        default -> null;
                    };
            if (jdkAlg == null) return false;
            int iterations = Integer.parseInt(params[2]);
            byte[] salt = parts[1].getBytes(StandardCharsets.UTF_8);
            byte[] expected = HexFormat.of().parseHex(parts[2]);

            var factory = javax.crypto.SecretKeyFactory.getInstance(jdkAlg);
            var spec =
                    new javax.crypto.spec.PBEKeySpec(
                            raw.toCharArray(), salt, iterations, expected.length * 8);
            byte[] actual = factory.generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }
}
