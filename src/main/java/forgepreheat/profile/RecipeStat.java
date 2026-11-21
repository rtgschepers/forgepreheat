package forgepreheat.profile;

/** Accumulated addRecipe time for one report row. */
public final class RecipeStat {
    private long count;
    private long totalNanos;
    private long maxNanos;
    private String slowestId = "";

    void add(String recipeId, long nanos) {
        count++;
        totalNanos += nanos;
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

    public String slowestId() {
        return slowestId;
    }
}
