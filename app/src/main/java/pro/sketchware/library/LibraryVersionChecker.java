package pro.sketchware.library;

public final class LibraryVersionChecker {
    private LibraryVersionChecker() {}

    public static int compareVersions(String left, String right) {
        String[] a = normalize(left).split("\\.");
        String[] b = normalize(right).split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? parse(a[i]) : 0;
            int bv = i < b.length ? parse(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static String normalize(String v) { return v == null ? "0" : v.replaceAll("[^0-9.].*$", ""); }
    private static int parse(String v) { try { return Integer.parseInt(v); } catch (Exception e) { return 0; } }
}
