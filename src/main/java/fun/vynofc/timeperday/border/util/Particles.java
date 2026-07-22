package fun.vynofc.timeperday.border.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fun.vynofc.timeperday.border.BorderData;
import fun.vynofc.timeperday.border.BorderShape;

import java.util.ArrayList;
import java.util.List;

public final class Particles {

    private static int maxDistance = 8;
    private static int maxDistanceSquared = maxDistance * maxDistance;

    private Particles() {
    }

    public static void setMaxDistance(final int maxDistance) {
        Particles.maxDistance = maxDistance;
        maxDistanceSquared = maxDistance * maxDistance;
    }

    public static List<Location> at(final Player player, final BorderData border, final double offsetPercent) {
        final Location pos = player.getLocation();
        final List<Location> particles = new ArrayList<>();
        final BorderShape shapeType = border.getShapeType();

        if (shapeType == BorderShape.SQUARE || shapeType == BorderShape.RECTANGLE) {
            final double centerX = border.getCenterX();
            final double centerZ = border.getCenterZ();
            final double radiusX = border.getRadiusX();
            final double radiusZ = border.getRadiusZ();

            final double[][] edges = {
                {centerX - radiusX, centerZ - radiusZ, centerX + radiusX, centerZ - radiusZ},
                {centerX + radiusX, centerZ - radiusZ, centerX + radiusX, centerZ + radiusZ},
                {centerX + radiusX, centerZ + radiusZ, centerX - radiusX, centerZ + radiusZ},
                {centerX - radiusX, centerZ + radiusZ, centerX - radiusX, centerZ - radiusZ}
            };

            for (final double[] edge : edges) {
                final double p1x = edge[0], p1z = edge[1], p2x = edge[2], p2z = edge[3];
                final List<Location> edgePoints = edgePoints(pos, p1x, p1z, p2x, p2z, offsetPercent);
                particles.addAll(edgePoints);
            }
        } else if (shapeType == BorderShape.CIRCLE || shapeType == BorderShape.ELLIPSE) {
            final double centerX = border.getCenterX();
            final double centerZ = border.getCenterZ();
            final double radiusX = border.getRadiusX();
            final double radiusZ = border.getRadiusZ();
            final double radius = Math.min(radiusX, radiusZ);
            final double angle = Math.acos((2 * radius * radius - 1) / (2 * radius * radius));
            final double cameraAngle = Math.atan2(radiusX * (pos.getZ() - centerZ), radiusZ * (pos.getX() - centerX));
            final double startY = Math.floor(pos.getY());
            final double forwardStartAngle = Math.floor(cameraAngle / angle) * angle;
            final double backwardStartAngle = forwardStartAngle - angle;

            for (double da = backwardStartAngle; da > backwardStartAngle - Math.PI; da = da - angle) {
                final double startX = centerX + radiusX * Math.cos(da);
                final double startZ = centerZ + radiusZ * Math.sin(da);
                final double endX = centerX + radiusX * Math.cos(da + angle);
                final double endZ = centerZ + radiusZ * Math.sin(da + angle);
                final Location startLoc = new Location(pos.getWorld(), startX, startY, startZ);
                final List<Location> edgePoints = ellipseEdgePoints(pos, startLoc, endX - startX, endZ - startZ, offsetPercent,
                        Math.min(startX, endX), Math.min(startZ, endZ), Math.max(startX, endX), Math.max(startZ, endZ));
                if (edgePoints.isEmpty()) {
                    break;
                }
                particles.addAll(edgePoints);
            }

            for (double da = forwardStartAngle; da < forwardStartAngle + Math.PI; da = da + angle) {
                final double startX = centerX + radiusX * Math.cos(da);
                final double startZ = centerZ + radiusZ * Math.sin(da);
                final double endX = centerX + radiusX * Math.cos(da + angle);
                final double endZ = centerZ + radiusZ * Math.sin(da + angle);
                final Location startLoc = new Location(pos.getWorld(), startX, startY, startZ);
                final List<Location> edgePoints = ellipseEdgePoints(pos, startLoc, endX - startX, endZ - startZ, offsetPercent,
                        Math.min(startX, endX), Math.min(startZ, endZ), Math.max(startX, endX), Math.max(startZ, endZ));
                if (edgePoints.isEmpty()) {
                    break;
                }
                particles.addAll(edgePoints);
            }
        }
        return particles;
    }

    private static List<Location> edgePoints(final Location playerPos, final double p1x, final double p1z,
                                              final double p2x, final double p2z, final double offsetPercent) {
        final List<Location> allPoints = new ArrayList<>();
        final double px = playerPos.getX();
        final double pz = playerPos.getZ();
        final double py = playerPos.getY();

        final double dx = p2x - p1x;
        final double dz = p2z - p1z;
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) {
            return allPoints;
        }
        final double unitX = dx / len;
        final double unitZ = dz / len;

        final double t = ((px - p1x) * unitX + (pz - p1z) * unitZ);
        final double closestX = p1x + unitX * t;
        final double closestZ = p1z + unitZ * t;

        final double distToClosest = Math.sqrt((px - closestX) * (px - closestX) + (pz - closestZ) * (pz - closestZ));
        if (distToClosest > maxDistance + 2) {
            return allPoints;
        }

        final double startY = Math.floor(py);
        final double startX;
        final double startZ;
        if (t < 0) {
            startX = p1x;
            startZ = p1z;
        } else if (t > len) {
            startX = p2x;
            startZ = p2z;
        } else {
            startX = closestX;
            startZ = closestZ;
        }

        final double minX = Math.min(p1x, p2x);
        final double minZ = Math.min(p1z, p2z);
        final double maxX = Math.max(p1x, p2x);
        final double maxZ = Math.max(p1z, p2z);

        for (double step = -unitX, stepZ = -unitZ; ; step -= unitX, stepZ -= unitZ) {
            final double x = startX + step;
            final double z = startZ + stepZ;
            final double distSq = (px - x) * (px - x) + (pz - z) * (pz - z);
            if (distSq > maxDistanceSquared + 4) {
                break;
            }
            allPoints.addAll(verticalPoints(playerPos, x, startY, z, offsetPercent, unitX, unitZ, minX, minZ, maxX, maxZ));
        }
        for (double step = 0, stepZ = 0; ; step += unitX, stepZ += unitZ) {
            final double x = startX + step;
            final double z = startZ + stepZ;
            final double distSq = (px - x) * (px - x) + (pz - z) * (pz - z);
            if (distSq > maxDistanceSquared + 4) {
                break;
            }
            allPoints.addAll(verticalPoints(playerPos, x, startY, z, offsetPercent, unitX, unitZ, minX, minZ, maxX, maxZ));
        }
        return allPoints;
    }

    private static List<Location> ellipseEdgePoints(final Location playerPos, final Location startPos,
                                                     final double unitX, final double unitZ,
                                                     final double offsetPercent, final double minX, final double minZ,
                                                     final double maxX, final double maxZ) {
        final List<Location> allPoints = new ArrayList<>();
        final double startX = startPos.getX();
        final double startY = startPos.getY();
        final double startZ = startPos.getZ();
        final double len = Math.sqrt(unitX * unitX + unitZ * unitZ);
        if (len < 0.001) {
            return allPoints;
        }
        final double ux = unitX / len;
        final double uz = unitZ / len;
        final double offsetX = startX + ux * offsetPercent;
        final double offsetZ = startZ + uz * offsetPercent;

        if (!(offsetX >= minX && offsetX <= maxX && offsetZ >= minZ && offsetZ <= maxZ)) {
            return allPoints;
        }

        allPoints.addAll(verticalPoints(playerPos, offsetX, startY - offsetPercent, offsetZ, offsetPercent, ux, uz, minX, minZ, maxX, maxZ));
        return allPoints;
    }

    private static List<Location> verticalPoints(final Location playerPos, final double x, final double y, final double z,
                                                  final double offsetPercent, final double unitX, final double unitZ,
                                                  final double minX, final double minZ, final double maxX, final double maxZ) {
        final List<Location> points = new ArrayList<>();
        final double unitOffsetX = unitX * offsetPercent;
        final double unitOffsetZ = unitZ * offsetPercent;
        final double offsetX = x + unitOffsetX;
        final double offsetZ = z + unitOffsetZ;

        if (!(offsetX >= minX && offsetX <= maxX && offsetZ >= minZ && offsetZ <= maxZ)) {
            return points;
        }

        final Location start = new Location(playerPos.getWorld(), offsetX, y - offsetPercent, offsetZ);
        if (playerPos.distanceSquared(start) > maxDistanceSquared) {
            return points;
        }
        points.add(start);

        for (double dy = 0; ; ++dy) {
            final double up = y + dy;
            final double down = y - dy;
            final Location upPos = new Location(playerPos.getWorld(), offsetX, up - offsetPercent, offsetZ);
            final Location downPos = new Location(playerPos.getWorld(), offsetX, down - offsetPercent, offsetZ);
            final boolean upInside = playerPos.distanceSquared(upPos) <= maxDistanceSquared;
            final boolean downInside = playerPos.distanceSquared(downPos) <= maxDistanceSquared;
            if (!upInside && !downInside) {
                break;
            }
            if (upInside) {
                points.add(upPos);
            }
            if (downInside) {
                points.add(downPos);
            }
        }
        return points;
    }
}