package forgepreheat.core;

import java.util.List;

/**
 * The element identities of a list at one moment. Caches keyed by list identity use it to detect a mod refilling the
 * same list object; reference checks are far cheaper than the work being cached.
 */
public final class ListSnapshot {
    private final Object[] elements;

    private ListSnapshot(Object[] elements) {
        this.elements = elements;
    }

    public static ListSnapshot of(List<?> list) {
        return new ListSnapshot(list.toArray());
    }

    public boolean matches(List<?> list) {
        if (elements.length != list.size()) {
            return false;
        }
        int i = 0;
        for (Object o : list) {
            if (o != elements[i++]) {
                return false;
            }
        }
        return true;
    }
}
