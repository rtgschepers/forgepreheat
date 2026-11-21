package forgepreheat.core;

/** Something that lives for one JEI start: an optimization or the profiler itself. */
public interface Feature {
    void begin();

    void end();

    /** One line for the report header; called before {@link #end()} so statistics are still available. */
    String summary();
}
