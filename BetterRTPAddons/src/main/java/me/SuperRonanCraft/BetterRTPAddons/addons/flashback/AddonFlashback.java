package me.SuperRonanCraft.BetterRTPAddons.addons.flashback;

import me.SuperRonanCraft.BetterRTP.player.commands.RTPCommand;
import me.SuperRonanCraft.BetterRTP.player.rtp.RTP_TYPE;
import me.SuperRonanCraft.BetterRTP.references.customEvents.RTP_TeleportPostEvent;
import me.SuperRonanCraft.BetterRTPAddons.Addon;
import me.SuperRonanCraft.BetterRTPAddons.util.Files;
import me.SuperRonanCraft.BetterRTPAddons.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//When rtp'ing, a player will be teleported back to old location after a set amount of time
public class AddonFlashback implements Addon, Listener {

    private final String name = "Flashback";

    private Long time;
    public final FlashbackMessages msgs = new FlashbackMessages();
    public final FlashbackDatabase database = new FlashbackDatabase();
    List<FlashbackPlayer> players = new ArrayList<>();
    HashMap<Long, String> warnings = new HashMap<>();

    public boolean isEnabled() {
        return getFile(Files.FILETYPE.CONFIG).getBoolean(name + ".Enabled");
    }

    @Override
    public void load() {
        Files.FILETYPE file = getFile(Files.FILETYPE.CONFIG);
        this.time = file.getConfig().getLong(name + ".Timer.Delay");
        this.database.load(FlashbackDatabase.Columns.values());

        warnings.clear();
        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());
        List<Map<?, ?>> override_map = file.getConfig().getMapList(name + ".Timer.Warnings");
        for (Map<?, ?> m : override_map)
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                try {
                    Long secs = getLong(entry.getKey().toString());
                    warnings.put(secs, entry.getValue().toString());
                    Main.getInstance().getLogger().info("- Warnings: Time: '" + entry.getKey() + "' Message: '" + entry.getValue() + "' added");
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    System.out.println("Warning value '" + entry.getKey().toString() + "' is " +
                            "invalid! Please make sure to format [- INTEGER: 'Message']");
                }
            }
        for (Player p : Bukkit.getOnlinePlayers())
            loadPlayer(p);
    }

    private Long getLong(String str) throws NumberFormatException {
        return Long.valueOf(str);
    }

    @Override
    public void unload() {
        for (FlashbackPlayer fbp : players)
            fbp.cancel();
        players.clear();
        HandlerList.unregisterAll(this);
    }

    @Override
    public RTPCommand getCmd() {
        return null;
    }

    @EventHandler
    void onTeleport(RTP_TeleportPostEvent e) { //Create a timer to teleport player back
        if (e.getType() != RTP_TYPE.ADDON_PORTAL &&
                e.getType() != RTP_TYPE.JOIN &&
                e.getType() != RTP_TYPE.TEST) {
            cancelPlayer(e.getPlayer());
            players.add(new FlashbackPlayer(this, e.getPlayer(), e.getOldLocation(), time, warnings));
        }
    }

    @EventHandler
    void onJoin(PlayerJoinEvent e) {
        loadPlayer(e.getPlayer());
    }

    @EventHandler
    void onLeave(PlayerQuitEvent e) {
        cancelPlayer(e.getPlayer());
    }

    private void cancelPlayer(Player p) {
        for (FlashbackPlayer fbp : new ArrayList<>(players)) // Avoid ConcurrentModificationException
            if (fbp.p == p) {
                fbp.cancel();
                // It seems FlashbackPlayer is responsible for removing itself from db via completed()
                // but only if it's not cancelled. If cancelled, we might need to remove it here.
                // However, FlashbackPlayer.cancel doesn't call completed().
                // Let's assume for now that if a task is cancelled, its db entry should also be removed.
                AsyncHandler.async(() -> database.removePlayer(fbp.p));
                players.remove(fbp); // Remove from active players list
            }
    }

    void loadPlayer(Player p) {
        //FlashbackPlayerInfo info = database.getPlayer(p);
        AsyncHandler.async(() -> {
            FlashbackPlayerInfo info = database.getPlayer(p);
            if (info != null) {
                long _time = (System.currentTimeMillis() - info.getTime()) / 1000;
                if (_time < 0) { //Still has time to go back
                    _time *= -1;
                    long final_time = _time;
                    AsyncHandler.syncAtEntity(p, () -> players.add(new FlashbackPlayer(this, p, info.getLocation(), final_time, warnings)));
                } else { //Overdue! Teleport them back NOW!
                    AsyncHandler.syncAtEntity(p, () -> players.add(new FlashbackPlayer(this, p, info.getLocation(), 0L, warnings)));
                }
            }
        });
    }
}
