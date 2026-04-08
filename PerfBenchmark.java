import java.util.HashMap;
import java.util.ArrayList;

public class PerfBenchmark {

    public static void main(String[] args) {
        ArrayList<HashMap<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("value", "android:name=\"test" + i + "\"");
            data.add(map);
        }

        // Baseline (Old logic)
        long startOld = System.nanoTime();
        for (int i = 0; i < data.size(); i++) {
            String value = (String) data.get(i).get("value");
            int i1 = ((String) data.get(i).get("value")).indexOf(":");
            int i2 = ((String) data.get(i).get("value")).indexOf(":");
            int i3 = ((String) data.get(i).get("value")).indexOf("=") + 1;
            int i4 = ((String) data.get(i).get("value")).indexOf("\"");
            int i5 = ((String) data.get(i).get("value")).length();

            // Simulating span setting ignoring objects
            int span1Start = 0;
            int span1End = i1;
            int span2Start = i2;
            int span2End = i3;
            int span3Start = i4;
            int span3End = i5;
        }
        long endOld = System.nanoTime();

        // Optimized logic
        long startNew = System.nanoTime();
        for (int i = 0; i < data.size(); i++) {
            String value = (String) data.get(i).get("value");
            int colonIndex = value.indexOf(":");
            int equalsIndex = value.indexOf("=");
            int quoteIndex = value.indexOf("\"");
            int len = value.length();

            int span1Start = 0;
            int span1End = colonIndex;
            int span2Start = colonIndex;
            int span2End = equalsIndex + 1;
            int span3Start = quoteIndex;
            int span3End = len;
        }
        long endNew = System.nanoTime();

        System.out.println("Old string ops (ns): " + (endOld - startOld));
        System.out.println("New string ops (ns): " + (endNew - startNew));
        System.out.println("Improvement: " + String.format("%.2f", (double)(endOld - startOld) / (endNew - startNew)) + "x faster");
    }
}
