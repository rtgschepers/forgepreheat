package forgepreheat.profile;

/** Accumulated addRecipe time for one report row, with the ingredient setup and index parts of it. */
public final class RecipeStat {
    private long count;
    private long totalNanos;
    private long maxNanos;
    private long setupNanos;
    private long indexNanos;
    private String slowestId = "";

    void add(String recipeId, long nanos, long setup, long index) {
        count++;
        totalNanos += nanos;
        setupNanos += setup;
        indexNanos += index;
        if (nanos > maxNanos) {
            maxNanos = nanos;
            slowestId = recipeId;
        }
    }

    public long count() {
        return count;
    }

    public long totalNanos() {
        return totalNanos;
    }

    public long maxNanos() {
        return maxNanos;
    }

    public long setupNanos() {
        return setupNanos;
    }

    public long indexNanos() {
        return indexNanos;
    }

    public String slowestId() {
        return slowestId;
    }
}
