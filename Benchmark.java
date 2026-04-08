import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

public class Benchmark {
    public static void main(String[] args) {
        ArrayList<String> data = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            data.add("Type" + i + "   name" + i);
        }

        long start = System.nanoTime();
        int sum1 = 0;
        for (int j = 0; j < 100; j++) {
            for (String s : data) {
                Matcher matcher = Pattern.compile("^(\\w+)[\\s]+(\\w+)").matcher(s);
                while (matcher.find()) {
                    sum1 += matcher.group(2).length();
                }
            }
        }
        long time1 = System.nanoTime() - start;

        long start2 = System.nanoTime();
        int sum2 = 0;
        Pattern pattern = Pattern.compile("^(\\w+)[\\s]+(\\w+)");
        for (int j = 0; j < 100; j++) {
            for (String s : data) {
                Matcher matcher = pattern.matcher(s);
                while (matcher.find()) {
                    sum2 += matcher.group(2).length();
                }
            }
        }
        long time2 = System.nanoTime() - start2;

        System.out.println("Time with Pattern.compile in loop: " + (time1 / 1_000_000) + " ms");
        System.out.println("Time with Pattern.compile outside loop: " + (time2 / 1_000_000) + " ms");
    }
}
