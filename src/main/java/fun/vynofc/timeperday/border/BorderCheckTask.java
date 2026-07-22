package fun.vynofc.timeperday.border;

import fun.vynofc.timeperday.TimePerDayPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.Sound;

public class BorderCheckTask implements Runnable {

    private final BorderManager borderManager;
    private final TimePerDayPlugin plugin;

    public BorderCheckTask(BorderManager borderManager, TimePerDayPlugin plugin) {
        this.borderManager = borderManager;
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> checkPlayer(player), null);
        }
    }

    private void checkPlayer(Player player) {
        if (!player.isOnline()) {
            return;
        }

        World world = player.getWorld();
        BorderData border = borderManager.getBorder(world.getName());
        if (border == null) {
            return;
        }

        PlayerData playerData = borderManager.getPlayerData(player.getUniqueId());

        if (border.isBypassing(player.getUniqueId())
                || player.hasPermission("timeperday.border.bypass.move")) {
            return;
        }

        Location loc = player.getLocation();
        if (border.isBounding(loc.getX(), loc.getZ())) {
            playerData.setLastLocation(loc.clone());
            return;
        }

        BorderWrapType wrapType = border.getWrapType();
        Location redirect;
        if (wrapType != BorderWrapType.NONE) {
            Location wrapped = wrap(border, wrapType, loc.clone());
            if (wrapped != null) {
                redirect = wrapped;
            } else {
                Location lastLocation = playerData.getLastLocation().orElse(world.getSpawnLocation());
                lastLocation.setYaw(loc.getYaw());
                lastLocation.setPitch(loc.getPitch());
                redirect = lastLocation;
            }
        } else {
            Location lastLocation = playerData.getLastLocation().orElse(world.getSpawnLocation());
            lastLocation.setYaw(loc.getYaw());
            lastLocation.setPitch(loc.getPitch());
            redirect = lastLocation;
        }

        playEffect(world, loc);
        playSound(world, loc);
        player.teleport(redirect);
        playerData.setLastLocation(redirect.clone());
        sendMessage(player);
    }

    private void playEffect(World world, Location loc) {
        String effectName = borderManager.getEffect();
        if (effectName == null || effectName.isEmpty()) {
            return;
        }
        try {
            Particle particle = Particle.valueOf(effectName.toUpperCase());
            world.spawnParticle(particle, loc, 5, 0.5, 0.5, 0.5, 0);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void playSound(World world, Location loc) {
        String soundName = borderManager.getSound();
        if (soundName == null || soundName.isEmpty()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            world.playSound(loc, sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void sendMessage(Player player) {
        String msg = borderManager.getMessage();
        if (msg == null || msg.isEmpty()) {
            return;
        }
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
        if (borderManager.useActionBar()) {
            player.sendActionBar(component);
        } else {
            player.sendMessage(component);
        }
    }

    private Location wrap(BorderData border, BorderWrapType wrapType, Location loc) {
        boolean rectangle = border.getShapeType() == BorderShape.SQUARE
                || border.getShapeType() == BorderShape.RECTANGLE;

        return switch (wrapType) {
            case DEFAULT -> rectangle ? wrapBoth(border, loc) : wrapRadial(border, loc);
            case BOTH -> wrapBoth(border, loc);
            case RADIAL -> wrapRadial(border, loc);
            case X -> wrapX(border, loc);
            case Z -> wrapZ(border, loc);
            case EARTH -> rectangle ? wrapEarth(border, loc) : null;
            default -> null;
        };
    }

    private Location wrapBoth(BorderData border, Location loc) {
        boolean wrappedX = wrapX(border, loc) != null;
        boolean wrappedZ = wrapZ(border, loc) != null;
        return (wrappedX || wrappedZ) ? loc : null;
    }

    private Location wrapRadial(BorderData border, Location loc) {
        double cx = border.getCenterX();
        double cz = border.getCenterZ();
        double angle = Math.atan2(loc.getZ() - cz, loc.getX() - cx);
        double rx = border.getRadiusX();
        double rz = border.getRadiusZ();

        double nx, nz;
        if (border.getShapeType() == BorderShape.CIRCLE || border.getShapeType() == BorderShape.ELLIPSE) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double denom = Math.sqrt((cos * cos) / (rx * rx) + (sin * sin) / (rz * rz));
            nx = cx + (cos / denom);
            nz = cz + (sin / denom);
        } else {
            double halfWidth = border.getRadiusX();
            double halfHeight = border.getRadiusZ();
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double tx = Double.POSITIVE_INFINITY;
            double tz = Double.POSITIVE_INFINITY;
            if (Math.abs(cos) > 1e-10) {
                tx = halfWidth / Math.abs(cos);
            }
            if (Math.abs(sin) > 1e-10) {
                tz = halfHeight / Math.abs(sin);
            }
            double t = Math.min(tx, tz);
            nx = cx + cos * t;
            nz = cz + sin * t;
        }

        double dx = nx - cx;
        double dz = nz - cz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0) {
            dx = dx / len * 3;
            dz = dz / len * 3;
        }

        loc.setX(nx + dx);
        loc.setZ(nz + dz);
        loc.setY(getSafeY(loc));
        return loc;
    }

    private Location wrapX(BorderData border, Location loc) {
        double minX = border.getCenterX() - border.getRadiusX();
        double maxX = border.getCenterX() + border.getRadiusX();
        if (loc.getX() <= minX) {
            loc.setX(maxX - 3);
        } else if (loc.getX() >= maxX) {
            loc.setX(minX + 3);
        } else {
            return null;
        }
        double minZ = border.getCenterZ() - border.getRadiusZ();
        double maxZ = border.getCenterZ() + border.getRadiusZ();
        if (loc.getZ() <= minZ || loc.getZ() >= maxZ) {
            return null;
        }
        loc.setY(getSafeY(loc));
        return loc;
    }

    private Location wrapZ(BorderData border, Location loc) {
        double minZ = border.getCenterZ() - border.getRadiusZ();
        double maxZ = border.getCenterZ() + border.getRadiusZ();
        if (loc.getZ() <= minZ) {
            loc.setZ(maxZ - 3);
        } else if (loc.getZ() >= maxZ) {
            loc.setZ(minZ + 3);
        } else {
            return null;
        }
        double minX = border.getCenterX() - border.getRadiusX();
        double maxX = border.getCenterX() + border.getRadiusX();
        if (loc.getX() <= minX || loc.getX() >= maxX) {
            return null;
        }
        loc.setY(getSafeY(loc));
        return loc;
    }

    private Location wrapEarth(BorderData border, Location loc) {
        wrapX(border, loc);
        double minZ = border.getCenterZ() - border.getRadiusZ();
        double maxZ = border.getCenterZ() + border.getRadiusZ();
        double centerX = border.getCenterX();
        double meridianDistance = centerX - loc.getX();
        if (loc.getZ() <= minZ) {
            loc.setX(centerX + meridianDistance);
            loc.setZ(minZ + 3);
            loc.setYaw(0);
        } else if (loc.getZ() >= maxZ) {
            loc.setX(centerX + meridianDistance);
            loc.setZ(maxZ - 3);
            loc.setYaw(180);
        }
        loc.setY(getSafeY(loc));
        return loc;
    }

    private double getSafeY(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return 64;
        }
        int y = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        return Math.max(y, world.getMinHeight()) + 1;
    }
}