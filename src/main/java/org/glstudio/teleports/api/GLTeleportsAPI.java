package org.glstudio.teleports.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.HomeShare;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.TeleportRecord;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.Warp;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The public API of GL-Teleports.
 *
 * <h2>Getting hold of it</h2>
 *
 * Obtained from Bukkit's service manager. There is deliberately no static accessor, so a plugin
 * that loads before GL-Teleports gets a clean {@code null} it can check rather than an exception
 * from somewhere unrelated.
 *
 * <pre>{@code
 * RegisteredServiceProvider<GLTeleportsAPI> rsp =
 *         Bukkit.getServicesManager().getRegistration(GLTeleportsAPI.class);
 *
 * if (rsp == null) {
 *     getLogger().warning("GL-Teleports is unavailable.");
 *     return;
 * }
 *
 * GLTeleportsAPI teleports = rsp.getProvider();
 * }</pre>
 *
 * The registration is absent both when GL-Teleports is not installed and when it failed to start,
 * so check it every time rather than assuming presence from your {@code plugin.yml}. Declaring
 * {@code softdepend: [GL-Teleports]} is still worth doing: it fixes the load order.
 *
 * <h2>Threading</h2>
 *
 * Two rules, and they cover everything here.
 *
 * <p><b>Methods returning {@link CompletableFuture} complete off the main thread.</b> Their
 * callbacks run on a storage thread where the Bukkit API is not legal. Hop back before touching
 * anything in the world:
 *
 * <pre>{@code
 * api.getHomes(uuid).thenAccept(homes ->
 *         Bukkit.getScheduler().runTask(plugin, () -> {
 *             // safe to touch Bukkit here
 *         }));
 * }</pre>
 *
 * Always terminate the future. One whose failure nobody observes reports nothing at all — neither
 * a result nor an exception — so finish the chain with {@code exceptionally} or equivalent.
 *
 * <p><b>Methods returning {@code void} or a plain value must be called from the main thread.</b>
 * The ones that move players do so because they touch the world; the cheap boolean lookups do so
 * because they read state the main thread owns.
 *
 * <h2>Scope</h2>
 *
 * This interface and the events in {@code org.glstudio.teleports.api.event} are the supported
 * surface. The plugin's managers are reachable through its main class but are not API and change
 * without notice.
 *
 * @see org.glstudio.teleports.api.event.PreTeleportEvent PreTeleportEvent, to stop or redirect a teleport
 * @see org.glstudio.teleports.api.event.PostTeleportEvent PostTeleportEvent, to react once one has happened
 */
public interface GLTeleportsAPI {

    /**
     * The name this server answers to on the network.
     *
     * <p>Taken from {@code multi-server.server-name}. Returns {@code "unnamed-server"} when
     * unconfigured, which is also what standalone servers report.
     *
     * <p>This is the value stored in {@link Home#getServer()} and {@link Warp#getServer()}, so it
     * is what tells you whether a destination is local.
     *
     * @return this server's network name, never {@code null}
     */
    String serverName();

    /**
     * Whether cross-server teleports can happen right now.
     *
     * <p>Not the same as "configured": this reports {@code false} while Redis is unreachable, even
     * though the setting is on and a supervisor is retrying in the background. Use it to decide
     * whether to offer a remote destination, not to inspect the configuration.
     *
     * @return {@code true} if multi-server is enabled and the bus is up
     */
    boolean isNetworkAvailable();

    /**
     * Every home belonging to a player, on any server of the network.
     *
     * <p>Answered from cache while the player is online, so this usually costs no query.
     *
     * @param owner the player's unique id
     * @return their homes, newest ordering not guaranteed; an empty list if they have none, never
     *         {@code null}
     */
    CompletableFuture<List<Home>> getHomes(UUID owner);

    /**
     * One home by name.
     *
     * @param owner the player's unique id
     * @param name  the home's name, matched case-insensitively
     * @return the home, or {@code null} if that player has no home by that name
     */
    CompletableFuture<Home> getHome(UUID owner, String name);

    /**
     * How many homes this player's permissions allow in total.
     *
     * <p>This is the global cap only. Per-world and per-server caps
     * ({@code home-limits.per-world} / {@code per-server}) apply in addition to it and are not
     * exposed here; to find out whether a specific {@code /sethome} would be allowed, listen for
     * {@link org.glstudio.teleports.api.event.HomeCreateEvent}.
     *
     * @param player the player to measure
     * @return the limit, or {@code -1} for unlimited
     */
    int getHomeLimit(Player player);

    /**
     * Sends a player to one of their homes, or to one shared with them.
     *
     * <p><b>Main thread.</b> Runs the whole pipeline — cooldown, warm-up delay, cost, world
     * blacklist, safety check, effects — and crosses to another server when the home lives there.
     * If the home does not exist the player is told, using the configured message.
     *
     * <p>Returns before the teleport happens: there may be a countdown to wait out. Listen for
     * {@link org.glstudio.teleports.api.event.PostTeleportEvent} to learn when it completed.
     *
     * @param player the player to move
     * @param name   the home's name, matched case-insensitively
     */
    void teleportToHome(Player player, String name);

    /**
     * Every warp on the network.
     *
     * <p>Unfiltered: warps the viewer cannot use and warps closed for maintenance are all
     * included. Filter with {@link Warp#hasPermission()} and {@link #isWarpLocked(String)}.
     *
     * @return all warps, empty if there are none, never {@code null}
     */
    CompletableFuture<List<Warp>> getWarps();

    /**
     * One warp by name.
     *
     * @param name the warp's name, matched case-insensitively
     * @return the warp, or {@code null} if no warp has that name
     */
    CompletableFuture<Warp> getWarp(String name);

    /**
     * Sends a player to a warp.
     *
     * <p><b>Main thread.</b> As {@link #teleportToHome}, and additionally refuses when the player
     * lacks the warp's permission — reported as "no such warp", so hidden warps stay hidden — or
     * when the warp or its server is closed for maintenance. Any per-warp cost, cooldown or delay
     * overrides the global one.
     *
     * @param player the player to move
     * @param name   the warp's name, matched case-insensitively
     */
    void teleportToWarp(Player player, String name);

    /**
     * The network's spawn.
     *
     * <p>There is one spawn for the whole network, wherever it was set. Its
     * {@link SpawnLocation#getServer()} says which server holds it, which need not be this one.
     *
     * @return the spawn, or {@code null} if none has been set anywhere yet
     */
    CompletableFuture<SpawnLocation> getSpawn();

    /**
     * Sends a player to the network's spawn, transferring them to the server that holds it when
     * that is not this one.
     *
     * <p><b>Main thread.</b> Tells the player if no spawn has been set.
     *
     * @param player the player to move
     */
    void teleportToSpawn(Player player);

    /**
     * Sends a player to arbitrary coordinates through the plugin's pipeline.
     *
     * <p><b>Main thread.</b> {@code type} selects which configuration governs the teleport: its
     * cooldown, delay, cost, effects and world blacklist. Choose the type whose rules you want
     * applied.
     *
     * <p>There is no variant that skips the pipeline. Moving a player behind the plugin's back
     * would leave its protections — the safety check, post-teleport damage immunity, cancel-on-move
     * — unapplied, and the teleport unrecorded. If that is genuinely what you want, call
     * {@link Player#teleport(Location)} yourself.
     *
     * @param player      the player to move
     * @param destination where to put them
     * @param type        whose rules to apply
     */
    void teleport(Player player, Location destination, TeleportType type);

    /**
     * Whether a player is currently on cooldown for a teleport type.
     *
     * <p>Always {@code false} for players holding {@code golden.teleports.bypass.cooldown}.
     *
     * @param player the player to check
     * @param type   the teleport type
     * @return {@code true} while the cooldown is running
     */
    boolean hasCooldown(Player player, TeleportType type);

    /**
     * How long is left on a cooldown.
     *
     * <p>Rounded up, so a fraction of a second never reads as {@code 0} while the cooldown is
     * still in force. With {@code cooldowns.network} on, the value survives a server change.
     *
     * @param player the player to check
     * @param type   the teleport type
     * @return seconds remaining, or {@code 0} if not on cooldown
     */
    long remainingCooldownSeconds(Player player, TeleportType type);

    /**
     * A player's most recent teleports, newest first.
     *
     * @param player the player's unique id
     * @param limit  how many records at most; values below 1 are treated as 1
     * @return the records, or an empty list when {@code history.enabled} is off
     */
    CompletableFuture<List<TeleportRecord>> getHistory(UUID player, int limit);

    /**
     * The homes already in memory for a player, without a query.
     *
     * <p><b>Main thread.</b> For anything that runs often — a scoreboard, a tab-list line, a menu
     * being redrawn — where {@link #getHomes(UUID)} would mean a database round trip per call.
     *
     * <p>The cache is warmed when a player joins, so for anyone online this is the same list
     * {@code getHomes} would produce. For an offline player, or in the moment before their join
     * finishes loading, it is empty and a background refresh is started; ask again shortly.
     *
     * @param owner the player's unique id
     * @return their cached homes, empty when nothing is cached yet, never {@code null}
     */
    List<Home> cachedHomes(UUID owner);

    /**
     * How many homes a player has, from cache.
     *
     * <p><b>Main thread.</b> Shorthand for {@code cachedHomes(owner).size()}, with the same
     * caveat: {@code 0} can mean "none cached yet" rather than "none".
     *
     * @param owner the player's unique id
     * @return the number of homes currently cached
     */
    int cachedHomeCount(UUID owner);

    /**
     * How many more homes a player may set.
     *
     * <p><b>Main thread.</b> Computed from the cache, so it carries the same caveat as
     * {@link #cachedHomes(UUID)}. Per-world and per-server caps are not taken into account —
     * this is the global allowance only.
     *
     * @param player the player to measure
     * @return homes remaining, or {@link Integer#MAX_VALUE} when the limit is unlimited
     */
    int cachedHomesRemaining(Player player);

    /**
     * Every warp already in memory, without a query.
     *
     * <p><b>Main thread.</b> Unfiltered, like {@link #getWarps()}. Empty while the first load is
     * still in flight, which on a running server is only the first moments after startup.
     *
     * @return the cached warps, never {@code null}
     */
    List<Warp> cachedWarps();

    /**
     * Creates or replaces a home, as though the owner had run {@code /sethome}.
     *
     * <p>Fires {@link org.glstudio.teleports.api.event.HomeCreateEvent}, so other plugins can
     * refuse it. Home limits are <em>not</em> checked: this exists for plugins granting a home for
     * their own reasons — a plot claim, a rank reward — and those should not be blocked by a cap
     * meant for {@code /sethome}. Check {@link #getHomeLimit(Player)} yourself if you want it.
     *
     * <p>Sends the player no message; that is the caller's to decide.
     *
     * @param owner    the player the home belongs to, who must be online for the event to fire
     * @param name     the home's name; invalid names are rejected rather than sanitised
     * @param location where the home should point
     * @return {@code true} if it was written, {@code false} if the name was invalid or a handler
     *         cancelled it
     */
    CompletableFuture<Boolean> setHome(Player owner, String name, Location location);

    /**
     * Creates or replaces a home for a player who need not be online.
     *
     * <p>Fires no event: {@link org.glstudio.teleports.api.event.HomeCreateEvent} carries a
     * {@code Player} and there is none to supply. Use {@link #setHome(Player, String, Location)}
     * when the owner is online, so other plugins keep their say.
     *
     * @param owner    the owner's unique id
     * @param name     the home's name; invalid names are rejected
     * @param location where the home should point
     * @return {@code true} if it was written
     */
    CompletableFuture<Boolean> setHome(UUID owner, String name, Location location);

    /**
     * Deletes one of a player's homes.
     *
     * <p>Fires {@link org.glstudio.teleports.api.event.HomeDeleteEvent}. Any invitations to that
     * home go with it, since a grant pointing at a home that no longer exists is invisible to both
     * parties and would come back to life if the name were reused.
     *
     * @param owner the player the home belongs to
     * @param name  the home's name, matched case-insensitively
     * @return {@code true} if a home was removed, {@code false} if none existed or a handler
     *         cancelled it
     */
    CompletableFuture<Boolean> deleteHome(Player owner, String name);

    /**
     * Deletes a home belonging to a player who need not be online. Fires no event.
     *
     * @param owner the owner's unique id
     * @param name  the home's name, matched case-insensitively
     * @return {@code true} if a home was removed
     */
    CompletableFuture<Boolean> deleteHome(UUID owner, String name);

    /**
     * Creates or replaces a warp.
     *
     * <p>Fires {@link org.glstudio.teleports.api.event.WarpCreateEvent}. Replacing an existing
     * warp keeps its per-warp cost, cooldown and delay, so moving a warp a few blocks does not
     * silently reset its pricing.
     *
     * <p>Refuses when a warp of that name already exists on <em>another</em> server: warp names
     * are unique network-wide, and overwriting one would quietly move it here and strand everyone
     * who used it.
     *
     * @param creator    the player credited with creating it, for the event
     * @param name       the warp's name; invalid names are rejected
     * @param location   where the warp should point
     * @param permission the permission needed to use it, or {@code null} for a public warp
     * @return {@code true} if it was written
     */
    CompletableFuture<Boolean> setWarp(Player creator, String name, Location location, String permission);

    /**
     * Creates or replaces a warp with no player behind it. Fires no event.
     *
     * @param name       the warp's name; invalid names are rejected
     * @param location   where the warp should point
     * @param permission the permission needed to use it, or {@code null} for a public warp
     * @return {@code true} if it was written
     */
    CompletableFuture<Boolean> setWarp(String name, Location location, String permission);

    /**
     * Deletes a warp.
     *
     * <p>Fires {@link org.glstudio.teleports.api.event.WarpDeleteEvent}.
     *
     * @param actor the player credited with the removal, for the event
     * @param name  the warp's name, matched case-insensitively
     * @return {@code true} if a warp was removed, {@code false} if none existed or a handler
     *         cancelled it
     */
    CompletableFuture<Boolean> deleteWarp(Player actor, String name);

    /**
     * Deletes a warp with no player behind it. Fires no event.
     *
     * @param name the warp's name, matched case-insensitively
     * @return {@code true} if a warp was removed
     */
    CompletableFuture<Boolean> deleteWarp(String name);

    /**
     * Whether home sharing is turned on ({@code home-shares.enabled}).
     *
     * <p>When it is off, {@link #shareHome} refuses and {@link #canUseSharedHome} always answers
     * {@code false}.
     *
     * @return {@code true} if players may share homes
     */
    boolean isHomeSharingEnabled();

    /**
     * Grants a player access to one of another player's homes.
     *
     * <p>Re-granting to the same person replaces the previous grant, which is how a temporary
     * invitation is extended.
     *
     * @param owner           whose home it is
     * @param ownerName       their name, recorded for display; may be {@code null}
     * @param home            the home's name, matched case-insensitively
     * @param invitee         who is being let in
     * @param inviteeName     their name, recorded for display; may be {@code null}
     * @param durationSeconds how long the grant lasts; {@code 0} or less for permanent
     * @return {@code true} if the grant was written, {@code false} if sharing is off, the home
     *         does not exist, or owner and invitee are the same player
     */
    CompletableFuture<Boolean> shareHome(UUID owner, String ownerName, String home,
                                         UUID invitee, String inviteeName, long durationSeconds);

    /**
     * Revokes one grant.
     *
     * @param owner   whose home it is
     * @param home    the home's name, matched case-insensitively
     * @param invitee whose access is being taken away
     * @return {@code true} if a grant was removed, {@code false} if there was none
     */
    CompletableFuture<Boolean> unshareHome(UUID owner, String home, UUID invitee);

    /**
     * Whether one player may currently use another's home.
     *
     * <p>Read from storage every time rather than from a cache. Letting somebody into a home after
     * access was revoked is the one mistake this feature must not make, and a cache is exactly
     * where that would happen.
     *
     * @param visitor who wants in
     * @param owner   whose home it is
     * @param home    the home's name, matched case-insensitively
     * @return {@code true} if a live, unexpired grant exists
     */
    CompletableFuture<Boolean> canUseSharedHome(UUID visitor, UUID owner, String home);

    /**
     * Every grant a player has received, expired ones already filtered out.
     *
     * @param invitee the player who was invited
     * @return their live grants, never {@code null}
     */
    CompletableFuture<List<HomeShare>> getSharedWith(UUID invitee);

    /**
     * Every grant a player has handed out, expired ones already filtered out.
     *
     * @param owner the player who did the sharing
     * @return their live grants, never {@code null}
     */
    CompletableFuture<List<HomeShare>> getSharesBy(UUID owner);

    /**
     * Sends a player to a home shared with them.
     *
     * <p><b>Main thread.</b> Re-checks the grant before moving anybody, so a revoked invitation
     * cannot be used even by a caller holding a stale list. Tells the player if they have no
     * access.
     *
     * @param player    the visitor
     * @param owner     whose home it is
     * @param ownerName their name, for the refusal message
     * @param name      the home's name, matched case-insensitively
     */
    void teleportToSharedHome(Player player, UUID owner, String ownerName, String name);

    /**
     * Whether a warp is closed by {@code /glteleports lock}.
     *
     * <p>Reads memory, so it is cheap enough to call inside a loop. A lock past its expiry counts
     * as absent.
     *
     * @param warp the warp's name, matched case-insensitively
     * @return {@code true} while the warp is closed
     */
    boolean isWarpLocked(String warp);

    /**
     * Whether a whole server is closed to incoming teleports.
     *
     * @param server the server's network name, matched case-insensitively
     * @return {@code true} while the server is closed
     */
    boolean isServerLocked(String server);
}
