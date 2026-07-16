package gg.vynofc.timeperday.manager;

public record PlayerTimeSnapshot(long played, long limit, long remaining, double sessionPoints,
                                 double totalLevel, boolean whitelisted, boolean bypassPermission,
                                 boolean unlimited) {
}
