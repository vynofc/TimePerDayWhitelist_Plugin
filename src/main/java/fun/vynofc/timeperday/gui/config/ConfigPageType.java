package fun.vynofc.timeperday.gui.config;

public enum ConfigPageType {
    MAIN("Allgemein"),
    BORDER("Border"),
    WORLD_REGENERATION("Welt-Reset");

    private final String title;

    ConfigPageType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}