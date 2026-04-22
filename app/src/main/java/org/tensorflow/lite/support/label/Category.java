package org.tensorflow.lite.support.label;

/**
 * Mock Category class to resolve TFLite namespace conflicts at compile time.
 * This class will be provided by the actual TFLite runtime if present, 
 * but having it here allows us to exclude the conflicting -api artifacts.
 */
public class Category {
    private final String label;
    private final String displayName;
    private final float score;
    private final int index;

    public Category(String label, float score) {
        this(label, "", score, -1);
    }

    public Category(String label, String displayName, float score, int index) {
        this.label = label;
        this.displayName = displayName;
        this.score = score;
        this.index = index;
    }

    public String getLabel() { return label; }
    public String getDisplayName() { return displayName; }
    public float getScore() { return score; }
    public int getIndex() { return index; }

    @Override
    public String toString() {
        return "<Category \"" + label + "\" (displayName=" + displayName + " score=" + score + " index=" + index + ")>";
    }
}
