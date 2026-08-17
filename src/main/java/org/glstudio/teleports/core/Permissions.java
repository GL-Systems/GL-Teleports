package org.glstudio.teleports.core;

public final class Permissions {
    private static final String ROOT = "golden.teleports.";

    public static final String HOME_USE = ROOT + "home.use";
    public static final String HOME_SET = ROOT + "home.set";
    public static final String HOME_DELETE = ROOT + "home.delete";
    public static final String HOME_LIST = ROOT + "home.list";
    public static final String HOME_ADMIN = ROOT + "home.admin";
    public static final String HOME_SHARE = ROOT + "home.share";

    public static final String HOME_LIMIT = ROOT + "home.limit.";

    public static final String WARP_USE = ROOT + "warp.use";
    public static final String WARP_LIST = ROOT + "warp.list";
    public static final String WARP_SET = ROOT + "warp.set";
    public static final String WARP_DELETE = ROOT + "warp.delete";

    public static final String SPAWN_USE = ROOT + "spawn.use";
    public static final String SPAWN_SET = ROOT + "spawn.set";

    public static final String TPA_SEND = ROOT + "tpa.send";
    public static final String TPA_HERE = ROOT + "tpa.here";
    public static final String TPA_ACCEPT = ROOT + "tpa.accept";
    public static final String TPA_DENY = ROOT + "tpa.deny";
    public static final String TPA_CANCEL = ROOT + "tpa.cancel";
    public static final String TPA_TOGGLE = ROOT + "tpa.toggle";
    public static final String TPA_BLOCK = ROOT + "tpa.block";

    public static final String ADMIN_RELOAD = ROOT + "admin.reload";
    public static final String ADMIN_BACKUP = ROOT + "admin.backup";
    public static final String ADMIN_HISTORY = ROOT + "admin.history";
    public static final String ADMIN_LOCK = ROOT + "admin.lock";

    public static final String BYPASS_LOCK = ROOT + "bypass.lock";

    public static final String BYPASS_COOLDOWN = ROOT + "bypass.cooldown";
    public static final String BYPASS_DELAY = ROOT + "bypass.delay";
    public static final String BYPASS_COST = ROOT + "bypass.cost";
    public static final String BYPASS_SAFETY = ROOT + "bypass.safety";
    public static final String BYPASS_BLACKLIST = ROOT + "bypass.blacklist";

    private Permissions() {
    }
}
