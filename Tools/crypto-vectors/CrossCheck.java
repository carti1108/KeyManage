// 암호 테스트 벡터 교차 검증 (JDK 표준 라이브러리, 의존성 없음).
// Node WebCrypto로 만든 docs/crypto-test-vectors.json을 JDK 구현으로 다시 계산한다.
// 전처리·PBKDF2·HKDF에 더해 AES-256-GCM 암호화와 복호화(틀린 키 거절 포함)까지 확인한다.
//
// 사용법 (저장소 루트에서): java Tools/crypto-vectors/CrossCheck.java

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CrossCheck {

    private static final String TRIMMED = " \u0009\n\r 　";
    private static final int ITERATIONS = 600_000;

    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of("docs/crypto-test-vectors.json"), StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();

        String keySection = section(json, "keyDerivationCases", "encryptionCases");
        int keyCases = 0;
        byte[] asciiEncryptionKey = null;
        for (String c : objects(keySection)) {
            keyCases++;
            String name = field(c, "name");
            byte[] normalized = normalize(unescape(field(c, "password"))).getBytes(StandardCharsets.UTF_8);
            byte[] master = pbkdf2(normalized, b64(field(c, "salt")));
            byte[] enc = hkdf(master, "keymanage-enc");
            byte[] auth = hkdf(master, "keymanage-auth");
            check(failures, name + ".normalizedPasswordUtf8", normalized, b64(field(c, "normalizedPasswordUtf8")));
            check(failures, name + ".masterKey", master, b64(field(c, "masterKey")));
            check(failures, name + ".encryptionKey", enc, b64(field(c, "encryptionKey")));
            check(failures, name + ".authHash", auth, b64(field(c, "authHash")));
            if (name.equals("ascii-passphrase")) asciiEncryptionKey = enc;
        }

        String encryption = section(json, "encryptionCases", null);
        String plaintext = unescape(field(encryption, "plaintext"));
        byte[] iv = b64(field(encryption, "iv"));
        byte[] ciphertext = b64(field(encryption, "ciphertext"));
        byte[] tag = b64(field(encryption, "tag"));

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(asciiEncryptionKey, "AES"), new GCMParameterSpec(128, iv));
        byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        check(failures, "vault.ciphertext", Arrays.copyOf(sealed, sealed.length - 16), ciphertext);
        check(failures, "vault.tag", Arrays.copyOfRange(sealed, sealed.length - 16, sealed.length), tag);

        try {
            byte[] opened = decrypt(asciiEncryptionKey, iv, ciphertext, tag);
            if (!new String(opened, StandardCharsets.UTF_8).equals(plaintext)) failures.add("vault.decrypt");
        } catch (AEADBadTagException e) {
            failures.add("vault.decrypt(태그 불일치)");
        }

        byte[] wrongKey = asciiEncryptionKey.clone();
        wrongKey[0] ^= 1;
        try {
            decrypt(wrongKey, iv, ciphertext, tag);
            failures.add("vault.wrongKeyAccepted");
        } catch (AEADBadTagException expected) {
            // 틀린 키는 인증 태그 검증에서 거절돼야 한다 (vault-spec 3장)
        }

        if (!failures.isEmpty()) {
            System.out.println("불일치: " + String.join(", ", failures));
            System.exit(1);
        }
        System.out.println("JDK 교차 검증 통과: 키 유도 " + keyCases + "건, AES-256-GCM 암호화·복호화, 틀린 키 거절");
    }

    static String normalize(String password) {
        String s = Normalizer.normalize(password, Normalizer.Form.NFC);
        int start = 0;
        int end = s.length();
        while (start < end && TRIMMED.indexOf(s.charAt(start)) >= 0) start++;
        while (end > start && TRIMMED.indexOf(s.charAt(end - 1)) >= 0) end--;
        return s.substring(start, end);
    }

    // JDK의 PBEKeySpec은 char[]를 받으므로, UTF-8 바이트를 그대로 쓰도록 HMAC으로 PBKDF2를 직접 계산한다
    static byte[] pbkdf2(byte[] password, byte[] salt) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(password, "HmacSHA256"));
        mac.update(salt);
        mac.update(new byte[] {0, 0, 0, 1});
        byte[] u = mac.doFinal();
        byte[] result = u.clone();
        for (int i = 1; i < ITERATIONS; i++) {
            u = mac.doFinal(u);
            for (int j = 0; j < result.length; j++) result[j] ^= u[j];
        }
        return result;
    }

    static byte[] hkdf(byte[] key, String info) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        byte[] prk = mac.doFinal(key);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 1);
        return mac.doFinal();
    }

    static byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] tag) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.update(ciphertext);
        return cipher.doFinal(tag);
    }

    static void check(List<String> failures, String name, byte[] actual, byte[] expected) {
        if (!Arrays.equals(actual, expected)) failures.add(name);
    }

    static byte[] b64(String value) {
        return Base64.getDecoder().decode(value);
    }

    // 의존성 없이 읽기 위한 최소 JSON 처리: 이 파일은 생성기가 만드는 고정 형식이다
    static String section(String json, String key, String nextKey) {
        int start = json.indexOf("\"" + key + "\"");
        int end = nextKey == null ? json.length() : json.indexOf("\"" + nextKey + "\"");
        return json.substring(start, end);
    }

    static List<String> objects(String section) {
        List<String> result = new ArrayList<>();
        Matcher m = Pattern.compile("\\{[^{}]*\\}").matcher(section);
        while (m.find()) result.add(m.group());
        return result;
    }

    static String field(String object, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(object);
        if (!m.find()) throw new IllegalStateException("필드 없음: " + key);
        return m.group(1);
    }

    static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'u' -> {
                    out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                default -> throw new IllegalStateException("알 수 없는 이스케이프: \\" + n);
            }
        }
        return out.toString();
    }
}
