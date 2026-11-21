package forgepreheat.optimize;

/** Implemented on JEI's IngredientFilter by a mixin, so a held-back change notification can be sent later. */
public interface FilterListenerNotifier {
    void forgepreheat$notifyListeners();
}
