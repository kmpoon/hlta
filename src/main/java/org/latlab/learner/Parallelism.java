package org.latlab.learner;

public class Parallelism {

    private static Parallelism instance = new Parallelism();

    private int level = Runtime.getRuntime().availableProcessors();

    private Parallelism() {
    }

    public void setLevel(int l) {
        int np = Runtime.getRuntime().availableProcessors();
        if (l > np) {
            level = np;
            System.out.printf(
                    "The specified level of parallelism (%d) is larger than the number of processors (%d).  The number %d is used.\n",
                    l, np, level);
        } else {
            level = l;
            System.out.printf("The level of parallelism is set to %d.\n", level);
        }
    }

    public int getLevel() {
        return level;
    }

    public static Parallelism instance() {
        return instance;
    }
}
