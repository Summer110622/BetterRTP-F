package me.SuperRonanCraft.BetterRTPAddons.addons.logger;

import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.player.commands.RTPCommand;
import me.SuperRonanCraft.BetterRTP.player.commands.types.CmdReload;
import me.SuperRonanCraft.BetterRTP.references.customEvents.RTP_CommandEvent;
import me.SuperRonanCraft.BetterRTP.references.customEvents.RTP_CommandEvent_After;
import me.SuperRonanCraft.BetterRTP.references.customEvents.RTP_TeleportPostEvent;
import me.SuperRonanCraft.BetterRTPAddons.Addon;
import me.SuperRonanCraft.BetterRTPAddons.AddonsCommand;
import me.SuperRonanCraft.BetterRTPAddons.util.Files;
import me.SuperRonanCraft.BetterRTPAddons.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

public class AddonLogger implements Addon, Listener {

    private final String name = "Logger";
    private String format;
    private boolean toConsole;
    Logger logger;
    FileHandler handler;
    ConsoleHandler consoleHandler_rtp, consoleHandler_main;
    private final LoggerDatabase database = new LoggerDatabase();

    @Override
    public boolean isEnabled() {
        return getFile(Files.FILETYPE.CONFIG).getBoolean(name + ".Enabled");
    }

    @Override
    public void load() {
        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());
        this.format = getFile(Files.FILETYPE.CONFIG).getString(name + ".Format");
        this.toConsole = getFile(Files.FILETYPE.CONFIG).getBoolean(name + ".LogToConsole");
        try {
            File f = new File(Main.getInstance().getDataFolder() + File.separator + "log.txt");
            handler = new FileHandler(f.getPath(), true);
            handler.setFormatter(new MyFormatter(this));
            logger = Logger.getLogger(Main.getInstance().getName() + "-Log");
            logger.setUseParentHandlers(this.toConsole); //Disable logging to console
            logger.addHandler(handler);
            //Log copying
            consoleHandler_rtp = new MyConsole(this.logger, BetterRTP.getInstance().getName());
            BetterRTP.getInstance().getLogger().addHandler(consoleHandler_rtp);
            consoleHandler_main = new MyConsole(this.logger, Main.getInstance().getName());
            Main.getInstance().getLogger().addHandler(consoleHandler_main);
        } catch (IOException e) {
            e.printStackTrace();
        }
        database.load(LoggerDatabase.Columns.values());
    }

    @Override
    public void unload() {
        HandlerList.unregisterAll(this);
        logger.removeHandler(handler);
        handler.close();
        BetterRTP.getInstance().getLogger().removeHandler(consoleHandler_rtp);
        Main.getInstance().getLogger().removeHandler(consoleHandler_main);
    }

    @Override
    public RTPCommand getCmd() {
        return null;
    }

    @EventHandler
    public void onCmd(RTP_CommandEvent e) {
        if (e instanceof RTP_CommandEvent_After) return;
        //Store required data for async task
        final String senderName = e.getSendi().getName();
        final String cmdName = e.getCmd().getName();
        final boolean isReload = e.getCmd() instanceof CmdReload;

        AsyncHandler.async(() -> {
            String _str = senderName + " executed `/rtp " + cmdName + "` at " + getDate();
            Level lvl = Level.INFO;
            if (isReload)
                lvl = Level.WARNING;
            log(_str, lvl);
        });
    }

    @EventHandler
    public void onTeleport(RTP_TeleportPostEvent e) {
        //Store required data for async task
        final String playerName = e.getPlayer().getName();
        final String locationString = e.getLocation().toString(); // Be careful with Location instances across threads if not immutable
        final String worldName = e.getLocation().getWorld().getName(); // Same as above for World
        // For database logging, ensure Player and Location objects are used safely if accessed across threads
        // It's often better to extract all necessary serializable data before going async.
        final Player player = e.getPlayer(); // Ok if only used for UUID/Name in DB thread
        final Location oldLocation = e.getOldLocation().clone(); // Clone to be safe
        final Location newLocation = e.getLocation().clone(); // Clone to be safe


        AsyncHandler.async(() -> {
            String _str = playerName + " has teleported to " + locationString
                    + " in world " + worldName
                    + " at" + getDate();
            log(_str, Level.INFO);
            database.log(player, oldLocation, newLocation);
        });
    }

    private void log(String str, Level lvl) {
        logger.log(lvl, str);
    }

    private String getDate() {
        SimpleDateFormat format = new SimpleDateFormat(this.format);
        return format.format(new Date());
    }

    //Make the logs come out readable
    static class MyFormatter extends Formatter {
        AddonLogger addon;

        MyFormatter(AddonLogger addon) {
            this.addon = addon;
        }

        @Override
        public String format(LogRecord record) {
            return addon.getDate() + " [" + record.getLevel().getName() + "]: " + record.getMessage() + '\n';
        }
    }

    //Copy one log to another log
    static class MyConsole extends ConsoleHandler {

        Level lvl;
        Logger logger;

        MyConsole(Logger logger, String name) {
            this.logger = logger;
            lvl = new MyLevel(name, Integer.MAX_VALUE);
        }

        @Override
        public void publish(LogRecord record) {
            logger.log(lvl, record.getMessage());
        }
    }

    static class MyLevel extends Level {

        protected MyLevel(String name, int value) {
            super(name, value);
        }
    }
}
