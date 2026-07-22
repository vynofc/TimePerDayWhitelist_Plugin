package fun.vynofc.timeperday.border;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BorderData {

    private String world;
    private double centerX;
    private double centerZ;
    private double radiusX;
    private double radiusZ;
    private String shape;
    private String wrap;
    private Set<UUID> bypassPlayers;

    public BorderData() {
        this.bypassPlayers = new HashSet<>();
        this.shape = "square";
        this.wrap = "none";
    }

    public BorderData(String world, double centerX, double centerZ, double radiusX, double radiusZ, String shape, String wrap) {
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.shape = shape != null ? shape : "square";
        this.wrap = wrap != null ? wrap : "none";
        this.bypassPlayers = new HashSet<>();
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double getCenterX() {
        return centerX;
    }

    public void setCenterX(double centerX) {
        this.centerX = centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public void setCenterZ(double centerZ) {
        this.centerZ = centerZ;
    }

    public double getRadiusX() {
        return radiusX;
    }

    public void setRadiusX(double radiusX) {
        this.radiusX = radiusX;
    }

    public double getRadiusZ() {
        return radiusZ;
    }

    public void setRadiusZ(double radiusZ) {
        this.radiusZ = radiusZ;
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
    }

    public BorderShape getShapeType() {
        return BorderShape.fromString(shape);
    }

    public String getWrap() {
        return BorderWrapType.fromString(wrap).name().toLowerCase();
    }

    public void setWrap(String wrap) {
        this.wrap = BorderWrapType.fromString(wrap).name().toLowerCase();
    }

    public BorderWrapType getWrapType() {
        return BorderWrapType.fromString(getWrap());
    }

    public Set<UUID> getBypassPlayers() {
        return bypassPlayers;
    }

    public void setBypassPlayers(Set<UUID> bypassPlayers) {
        this.bypassPlayers = bypassPlayers;
    }

    public boolean isBypassing(UUID uuid) {
        return bypassPlayers.contains(uuid);
    }

    public void setBypass(UUID uuid, boolean bypass) {
        if (bypass) {
            bypassPlayers.add(uuid);
        } else {
            bypassPlayers.remove(uuid);
        }
    }

    public boolean isBounding(double x, double z) {
        BorderShape shapeType = getShapeType();
        return switch (shapeType) {
            case SQUARE, RECTANGLE -> isBoundingSquare(x, z);
            case CIRCLE, ELLIPSE -> isBoundingEllipse(x, z);
        };
    }

    private boolean isBoundingSquare(double x, double z) {
        return x >= centerX - radiusX && x <= centerX + radiusX
                && z >= centerZ - radiusZ && z <= centerZ + radiusZ;
    }

    private boolean isBoundingEllipse(double x, double z) {
        double dx = (x - centerX) / radiusX;
        double dz = (z - centerZ) / radiusZ;
        return (dx * dx + dz * dz) <= 1.0;
    }
}