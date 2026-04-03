public class BenchmarkPalette {
    public static void main(String[] args) {
        String searchValue = "test";
        String[] titles = new String[10000];
        for(int i=0; i<titles.length; i++) {
            titles[i] = "Some Title Test " + i;
        }

        // Before
        long t1 = System.nanoTime();
        for(int i=0; i<1000; i++) {
            for(String title : titles) {
                boolean b = searchValue.isEmpty() || title.toLowerCase().contains(searchValue.toLowerCase());
            }
        }
        long t2 = System.nanoTime();
        System.out.println("Before: " + (t2-t1)/1000000.0 + " ms");

        // After
        long t3 = System.nanoTime();
        String searchValueLower = searchValue.toLowerCase();
        for(int i=0; i<1000; i++) {
            for(String title : titles) {
                boolean b = searchValueLower.isEmpty() || title.toLowerCase().contains(searchValueLower);
            }
        }
        long t4 = System.nanoTime();
        System.out.println("After: " + (t4-t3)/1000000.0 + " ms");
    }
}
