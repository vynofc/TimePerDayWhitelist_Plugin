package fun.vynofc.timeperday.border;

public enum BorderShape {
    SQUARE,
    RECTANGLE,
    CIRCLE,
    ELLIPSE;

    public static BorderShape fromString(String shape) {
        if (shape == null) {
            return SQUARE;
        }
        return switch (shape.toLowerCase()) {
            case "rectangle" -> RECTANGLE;
            case "circle" -> CIRCLE;
            case "ellipse" -> ELLIPSE;
            default -> SQUARE;
        };
    }
}