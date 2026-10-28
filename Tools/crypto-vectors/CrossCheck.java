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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        String json = Files.readString(findVectors(), StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();

        // 규격 값은 파일에서 읽지 않고 고정해 두고, 파일의 기록이 규격과 같은지만 본다
        if (!json.contains("\"iterations\": 600000") || !json.contains("\"encryptionInfo\": \"keymanage-enc\"")
                || !json.contains("\"authInfo\": \"keymanage-auth\"")) {
            failures.add("kdf·hkdf 파라미터가 규격과 다름");
        }

        Map<String, String> keyCases = new LinkedHashMap<>();
        Map<String, byte[]> encryptionKeys = new HashMap<>();
        for (String c : topLevelObjects(section(json, "keyDerivationCases", "encryptionCases"))) {
            String name = field(c, "name");
            keyCases.put(name, c);
            byte[] normalized = normalize(unescape(field(c, "password"))).getBytes(StandardCharsets.UTF_8);
            byte[] master = pbkdf2(normalized, b64(field(c, "salt")));
            byte[] enc = hkdf(master, "keymanage-enc");
            check(failures, name + ".normalizedPasswordUtf8", normalized, b64(field(c, "normalizedPasswordUtf8")));
            check(failures, name + ".masterKey", master, b64(field(c, "masterKey")));
            check(failures, name + ".encryptionKey", enc, b64(field(c, "encryptionKey")));
            check(failures, name + ".authHash", hkdf(master, "keymanage-auth"), b64(field(c, "authHash")));
            encryptionKeys.put(name, enc);
        }

        // 전처리 후 같은 비밀번호가 되는 케이스는 기준 케이스와 모든 값이 같아야 한다
        for (Map.Entry<String, String> e : keyCases.entrySet()) {
            String sameAs = optionalField(e.getValue(), "sameAs");
            if (sameAs == null) continue;
            String base = keyCases.get(sameAs);
            for (String f : List.of("normalizedPasswordUtf8", "masterKey", "encryptionKey", "authHash")) {
                if (base == null || !field(base, f).equals(field(e.getValue(), f))) failures.add(e.getKey() + " != " + sameAs);
            }
        }

        int encryptionCases = 0;
        for (String c : topLevelObjects(section(json, "encryptionCases", null))) {
            encryptionCases++;
            String name = field(c, "name");
            byte[] key = encryptionKeys.get(field(c, "keyFrom"));
            if (key == null) {
                failures.add(name + ": keyFrom 케이스가 없음");
                continue;
            }
            checkEncryption(failures, name, key, c);
        }
        if (encryptionCases == 0) failures.add("암호화 케이스가 없음");

        if (!failures.isEmpty()) {
            System.out.println("불일치: " + String.join(", ", failures));
            System.exit(1);
        }
        System.out.println("JDK 교차 검증 통과: 키 유도 " + keyCases.size() + "건, 암호화 " + encryptionCases
                + "건(AES-256-GCM 암호화·복호화, 틀린 키 거절), 같은 결과 케이스 확인");
    }

    static void checkEncryption(List<String> failures, String name, byte[] key, String c) throws Exception {
        String plaintext = unescape(field(c, "plaintext"));
        byte[] iv = b64(field(c, "iv"));
        byte[] ciphertext = b64(field(c, "ciphertext"));
        byte[] tag = b64(field(c, "tag"));

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        check(failures, name + ".ciphertext", Arrays.copyOf(sealed, sealed.length - 16), ciphertext);
        check(failures, name + ".tag", Arrays.copyOfRange(sealed, sealed.length - 16, sealed.length), tag);

        try {
            byte[] opened = decrypt(key, iv, ciphertext, tag);
            if (!new String(opened, StandardCharsets.UTF_8).equals(plaintext)) failures.add(name + ".decrypt");
        } catch (AEADBadTagException e) {
            failures.add(name + ".decrypt(태그 불일치)");
        }

        byte[] wrongKey = key.clone();
        wrongKey[0] ^= 1;
        try {
            decrypt(wrongKey, iv, ciphertext, tag);
            failures.add(name + ".wrongKeyAccepted");
        } catch (AEADBadTagException expected) {
            // 틀린 키는 인증 태그 검증에서 거절돼야 한다 (vault-spec 3장)
        }
    }

    // 실행 위치와 상관없이 위쪽 폴더로 올라가며 벡터 파일을 찾는다
    static Path findVectors() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("docs/crypto-test-vectors.json");
            if (Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("docs/crypto-test-vectors.json을 찾을 수 없음");
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

    // 배열 안의 최상위 객체만 꺼낸다. 문자열 안의 중괄호는 세지 않는다
    static List<String> topLevelObjects(String section) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        for (int i = section.indexOf('['); i < section.length(); i++) {
            char ch = section.charAt(i);
            if (inString) {
                if (ch == '\\') i++;
                else if (ch == '"') inString = false;
                continue;
            }
            if (ch == '"') inString = true;
            else if (ch == '{' && depth++ == 0) start = i;
            else if (ch == '}' && --depth == 0) result.add(section.substring(start, i + 1));
            else if (ch == ']' && depth == 0) break;
        }
        return result;
    }

    static String optionalField(String object, String key) {
        try {
            return field(object, key);
        } catch (IllegalStateException e) {
            return null;
        }
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
