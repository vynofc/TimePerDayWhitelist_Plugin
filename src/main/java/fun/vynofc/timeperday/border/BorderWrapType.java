package fun.vynofc.timeperday.border;

public enum BorderWrapType {
    NONE,
    DEFAULT,
    BOTH,
    RADIAL,
    X,
    Z,
    EARTH;

    public static BorderWrapType fromString(String type) {
        if (type == null) {
            return NONE;
        }
        return switch (type.toUpperCase()) {
            case "TRUE" -> DEFAULT;
            case "FALSE" -> NONE;
            default -> {
                try {
                    yield BorderWrapType.valueOf(type.toUpperCase());
                } catch (IllegalArgumentException e) {
                    yield NONE;
                }
            }
        };
    }
}