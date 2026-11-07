package org.glstudio.teleports.core.util;

/**
 * The permission nodes of the plugin.
 */
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
    public static final String WARP_OTHERS = ROOT + "warp.others";
    public static final String WARP_LIST = ROOT + "warp.list";
    public static final String WARP_SET = ROOT + "warp.set";
    public static final String WARP_DELETE = ROOT + "warp.delete";

    public static final String PLAYERWARP_USE = ROOT + "playerwarp.use";
    public static final String PLAYERWARP_SET = ROOT + "playerwarp.set";
    public static final String PLAYERWARP_REMOVE = ROOT + "playerwarp.remove";
    public static final String PLAYERWARP_LIST = ROOT + "playerwarp.list";
    public static final String PLAYERWARP_DESC = ROOT + "playerwarp.desc";
    public static final String PLAYERWARP_ICON = ROOT + "playerwarp.icon";
    public static final String PLAYERWARP_CATEGORY = ROOT + "playerwarp.category";
    public static final String PLAYERWARP_RATE = ROOT + "playerwarp.rate";
    public static final String PLAYERWARP_LOCK = ROOT + "playerwarp.lock";
    public static final String PLAYERWARP_COST = ROOT + "playerwarp.cost";
    public static final String PLAYERWARP_RESET = ROOT + "playerwarp.reset";
    public static final String PLAYERWARP_RENAME = ROOT + "playerwarp.rename";
    public static final String PLAYERWARP_SETOWNER = ROOT + "playerwarp.setowner";
    public static final String PLAYERWARP_PASSWORD = ROOT + "playerwarp.password";
    public static final String PLAYERWARP_WHITELIST = ROOT + "playerwarp.whitelist";
    public static final String PLAYERWARP_BAN = ROOT + "playerwarp.ban";
    public static final String PLAYERWARP_FAVOURITE = ROOT + "playerwarp.favourite";
    public static final String PLAYERWARP_SETTINGS = ROOT + "playerwarp.settings";

    public static final String PLAYERWARP_ADMIN_REMOVEALL = ROOT + "playerwarp.admin.removeall";
    public static final String PLAYERWARP_ADMIN_MANAGERS = ROOT + "playerwarp.admin.managers";
    public static final String PLAYERWARP_ADMIN_RELOAD = ROOT + "playerwarp.admin.reload";
    public static final String PLAYERWARP_ADMIN_VIEW = ROOT + "playerwarp.admin.view";

    public static final String PLAYERWARP_BYPASS_PASSWORD = ROOT + "playerwarp.bypass.password";
    public static final String PLAYERWARP_BYPASS_WHITELIST = ROOT + "playerwarp.bypass.whitelist";
    public static final String PLAYERWARP_BYPASS_BAN = ROOT + "playerwarp.bypass.ban";
    public static final String PLAYERWARP_BYPASS_LOCK = ROOT + "playerwarp.bypass.lock";
    public static final String PLAYERWARP_BYPASS_COST = ROOT + "playerwarp.bypass.cost";

    public static final String PLAYERWARP_LIMIT_PREFIX = "pw.limit.";
    public static final String PLAYERWARP_LIMIT_UNLIMITED = "pw.limit.unlimited";

    public static final String BACK_USE = ROOT + "back.use";

    public static final String SPAWN_USE = ROOT + "spawn.use";
    public static final String SPAWN_SET = ROOT + "spawn.set";
    public static final String SPAWN_OTHERS = ROOT + "spawn.others";

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
    public static final String ADMIN_MIGRATE = ROOT + "admin.migrate";
    public static final String ADMIN_TPOFFLINE = ROOT + "admin.tpoffline";

    public static final String BYPASS_LOCK = ROOT + "bypass.lock";

    public static final String BYPASS_COOLDOWN = ROOT + "bypass.cooldown";
    public static final String BYPASS_DELAY = ROOT + "bypass.delay";
    public static final String BYPASS_COST = ROOT + "bypass.cost";
    public static final String BYPASS_SAFETY = ROOT + "bypass.safety";
    public static final String BYPASS_BLACKLIST = ROOT + "bypass.blacklist";

    private Permissions() {
    }
}
