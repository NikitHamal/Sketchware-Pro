import java.util.*;
import java.util.regex.*;

public class Benchmark {
    private static String findFirstNumericOld(String content, List<String> keys) {
        for (String key : keys) {
            Matcher matcher = Pattern.compile("(?m)^[\\t ]*" + Pattern.quote(key) + "[\\t ]*=?[\\t ]*([0-9]+)").matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private static String findFirstNumericNew(String content, List<String> keys) {
        if (keys == null || keys.isEmpty()) return null;

        StringBuilder patternBuilder = new StringBuilder("(?m)^[\\t ]*(");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) patternBuilder.append("|");
            patternBuilder.append(Pattern.quote(keys.get(i)));
        }
        patternBuilder.append(")[\\t ]*=?[\\t ]*([0-9]+)");

        Matcher matcher = Pattern.compile(patternBuilder.toString()).matcher(content);

        String[] results = new String[keys.size()];
        int matches = 0;
        while (matcher.find()) {
            String matchKey = matcher.group(1);
            int idx = keys.indexOf(matchKey);
            if (idx != -1) {
                if (results[idx] == null) {
                    results[idx] = matcher.group(2).trim();
                    matches++;
                    if (idx == 0) return results[idx]; // early return if first key matched
                }
            }
        }

        if (matches > 0) {
            for (String res : results) {
                if (res != null) return res;
            }
        }
        return null;
    }

    private static final Map<List<String>, Pattern> PATTERN_CACHE = new HashMap<>();

    private static String findFirstNumericCached(String content, List<String> keys) {
        if (keys == null || keys.isEmpty()) return null;

        Pattern pattern = PATTERN_CACHE.computeIfAbsent(keys, k -> {
            StringBuilder patternBuilder = new StringBuilder("(?m)^[\\t ]*(");
            for (int i = 0; i < k.size(); i++) {
                if (i > 0) patternBuilder.append("|");
                patternBuilder.append(Pattern.quote(k.get(i)));
            }
            patternBuilder.append(")[\\t ]*=?[\\t ]*([0-9]+)");
            return Pattern.compile(patternBuilder.toString());
        });

        Matcher matcher = pattern.matcher(content);

        String[] results = new String[keys.size()];
        int matches = 0;
        while (matcher.find()) {
            String matchKey = matcher.group(1);
            int idx = keys.indexOf(matchKey);
            if (idx != -1) {
                if (results[idx] == null) {
                    results[idx] = matcher.group(2).trim();
                    matches++;
                    if (idx == 0) return results[idx]; // early return if first key matched
                }
            }
        }

        if (matches > 0) {
            for (String res : results) {
                if (res != null) return res;
            }
        }
        return null;
    }


    public static void main(String[] args) {
        String content = """
        android {
            namespace 'com.example.app'
            compileSdk 33

            defaultConfig {
                applicationId "com.example.app"
                minSdk 24
                targetSdk 33
                versionCode 1
                versionName "1.0"
            }
            buildTypes {
                release {
                    minifyEnabled false
                    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                }
            }
            compileOptions {
                sourceCompatibility JavaVersion.VERSION_1_8
                targetCompatibility JavaVersion.VERSION_1_8
            }
        }
        """;
        List<String> keys = Arrays.asList("targetSdk", "targetSdkVersion");

        long start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            findFirstNumericOld(content, keys);
        }
        long end = System.nanoTime();
        System.out.println("Old: " + (end - start) / 1_000_000 + "ms");

        start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            findFirstNumericNew(content, keys);
        }
        end = System.nanoTime();
        System.out.println("New (recompiled): " + (end - start) / 1_000_000 + "ms");

        start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            findFirstNumericCached(content, keys);
        }
        end = System.nanoTime();
        System.out.println("New (cached): " + (end - start) / 1_000_000 + "ms");
    }
}
