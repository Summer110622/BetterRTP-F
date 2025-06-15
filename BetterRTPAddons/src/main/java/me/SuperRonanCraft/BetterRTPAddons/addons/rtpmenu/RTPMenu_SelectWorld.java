package me.SuperRonanCraft.BetterRTPAddons.addons.rtpmenu;

import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.player.commands.types.CmdTeleport;
import me.SuperRonanCraft.BetterRTP.references.PermissionCheck;
import me.SuperRonanCraft.BetterRTP.references.PermissionNode;
import me.SuperRonanCraft.BetterRTP.references.messages.Message;
import me.SuperRonanCraft.BetterRTPAddons.util.Files;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RTPMenu_SelectWorld {

    public static boolean createInv(AddonRTPMenu pl, Player p) {
        // Part 1: Synchronous checks and data gathering (can stay on calling thread)
        List<World> bukkit_worlds = Bukkit.getWorlds(); // Potentially problematic on Folia if called from async, but current path is sync
        List<World> actual_worlds = new ArrayList<>();
        for (World world : bukkit_worlds) {
            if (pl.getWorlds().containsKey(world.getName()) && PermissionCheck.getAWorld(p, world.getName()))
                actual_worlds.add(world);
        }
        if (actual_worlds.isEmpty() || (actual_worlds.size() <= 1 && !BetterRTP.getInstance().getSettings().isDebug())) {
            CmdTeleport.teleport(p, "rtp", null, null); // This is a static helper, assumed to be Folia-safe or will be made so
            return false;
        }

        // Data for the scheduled task needs to be effectively final or copied
        final List<World> final_actual_worlds = new ArrayList<>(actual_worlds);
        final AddonRTPMenu final_pl = pl;
        final Player final_p = p;

        final_p.getScheduler().run(Main.getInstance(), task -> {
            // Part 2: Inventory creation and opening (must be on player's region thread)
            int lines = final_pl.getSettings().getLines();
            if (lines == 0)
                lines = Math.floorDiv(final_actual_worlds.size(), 9);
            if (lines < final_actual_worlds.size() / 9) lines++;
            Inventory inv = createInventory(color(final_p, final_pl.getSettings().getTitle()), Math.min(lines * 9, 6 * 9));

            HashMap<Integer, World> world_slots = centerWorlds(final_pl, final_actual_worlds); // Pass final_actual_worlds

            for (Map.Entry<Integer, World> world_entry : world_slots.entrySet()) { // Renamed to avoid conflict
                String worldName = world_entry.getValue().getName();
                RTPMenuWorldInfo worldInfo = final_pl.getWorlds().getOrDefault(worldName, new RTPMenuWorldInfo(worldName, Material.MAP, null, 0));
                int slot = world_entry.getKey();
                ItemStack item = new ItemStack(worldInfo.item, 1);
                ItemMeta meta = item.getItemMeta();
                assert meta != null;
                meta.setDisplayName(color(final_p, worldInfo.name));
                List<String> lore = new ArrayList<>(worldInfo.lore);
                lore.forEach(s -> lore.set(lore.indexOf(s), color(final_p, s).replace("%world%", world_entry.getValue().getName())));
                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(slot, item);
            }

            final_pl.getData(final_p).setMenuInv(inv);
            final_pl.getData(final_p).setWorldSlots(world_slots);
            final_p.openInventory(inv);
        }, null);

        return true; // Assume scheduling is successful
    }

    // actual_worlds param should be List<World> not ArrayList<World> for flexibility
    private static HashMap<Integer, World> centerWorlds(AddonRTPMenu pl, List<World> actual_worlds) {
        HashMap<Integer, World> map = new HashMap<>();
        if (actual_worlds.size() >= 9) {
            for (int i = 0; i < actual_worlds.size(); i++) {
                map.put(map.size(), actual_worlds.get(i));
                //actual_worlds.remove(0);
            }
            return map;
        }
        if (pl.getSettings().getAutoCenter()) {
            for (int i = 0; i < actual_worlds.size(); i++) {
                int offset = getSlotOffset(actual_worlds.size(), i);
                map.put(offset + i, actual_worlds.get(i));
            }
        } else {
            for (int i = 0; i < actual_worlds.size(); i++) {
                RTPMenuWorldInfo info = pl.getWorlds().get(actual_worlds.get(i).getName());
                if (info != null)
                    map.put(info.slot, actual_worlds.get(i));
            }
        }
        return map;
    }

    private static int getSlotOffset(int gear_to_show, int index) {
        if (gear_to_show % 2 == 0) { //Is Even
            switch (gear_to_show) {
                case 2:
                    switch (index) {
                        case 0: return 3;
                        case 1: return 4;
                    }
                    break;
                case 4:
                    switch (index) {
                        case 0:
                        case 1: return 2;
                        case 2:
                        case 3: return 3;
                    }
                    break;
                case 6:
                    switch (index) {
                        case 0:
                        case 1:
                        case 2: return 1;
                        case 3:
                        case 4:
                        case 5: return 2;
                    }
                    break;
                case 8:
                    if (index >= 4) return 1;
            }
        } else {
            switch (gear_to_show) {
                case 1: return 4;
                case 3: return 3;
                case 5: return 2;
                case 7: return 1;
            }
        }
        return 0;
    }

    private static String color(CommandSender sendi, String str) {
        return ChatColor.translateAlternateColorCodes('&', Message.placeholder(sendi, str));
    }

    private static Inventory createInventory(String title, int size) {
        title = Message.color(title);
        return Bukkit.createInventory(null, Math.max(Math.min(size, 54), 9), title);
    }

}
