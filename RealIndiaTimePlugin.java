package fun.litmc.realindiatime;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class RealIndiaTimePlugin extends JavaPlugin implements Listener {
    private ZoneId zone;
    private String mode;
    private double latitude;
    private double longitude;
    private LocalTime fixedSunrise;
    private LocalTime fixedSunset;
    private int updateSeconds;
    private boolean freezeCycle;
    private boolean blockSleep;
    private boolean blockPlayerTimeCommands;
    private boolean antiDrift;
    private long lastMinecraftTime = Long.MIN_VALUE;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        scheduleSync();
        getLogger().info("RealIndiaTime enabled for " + zone + " in mode " + mode + ".");
    }

    private void loadSettings() {
        reloadConfig();
        try {
            zone = ZoneId.of(getConfig().getString("timezone", "Asia/Kolkata"));
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Kolkata");
            getLogger().warning("Invalid timezone; using Asia/Kolkata.");
        }
        mode = getConfig().getString("mode", "REAL").toUpperCase(Locale.ROOT);
        latitude = getConfig().getDouble("location.latitude", 25.5941);
        longitude = getConfig().getDouble("location.longitude", 85.1376);
        fixedSunrise = parseTime(getConfig().getString("fixed.sunrise", "06:00"), LocalTime.of(6, 0));
        fixedSunset = parseTime(getConfig().getString("fixed.sunset", "18:00"), LocalTime.of(18, 0));
        updateSeconds = Math.max(1, getConfig().getInt("update-seconds", 10));
        freezeCycle = getConfig().getBoolean("freeze-vanilla-daylight-cycle", true);
        blockSleep = getConfig().getBoolean("block-sleep-at-night", true);
        blockPlayerTimeCommands = getConfig().getBoolean("block-player-time-commands", true);
        antiDrift = getConfig().getBoolean("anti-drift", true);
    }

    private LocalTime parseTime(String value, LocalTime fallback) {
        try { return LocalTime.parse(value); }
        catch (Exception e) { return fallback; }
    }

    private void scheduleSync() {
        long ticks = Math.max(1L, updateSeconds * 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::syncAllWorlds, 1L, ticks);
    }

    private void syncAllWorlds() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        SunWindow sun = mode.equals("REAL")
                ? SunriseSunset.calculate(now.toLocalDate(), latitude, longitude, zone)
                : new SunWindow(fixedSunrise, fixedSunset);
        long minecraftTime = minecraftTimeFor(now.toLocalTime(), sun);
        boolean daytime = isDay(now.toLocalTime(), sun);

        for (World world : Bukkit.getWorlds()) {
            if (!isConfiguredWorld(world.getName())) continue;
            if (freezeCycle) world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            if (antiDrift || world.getTime() != minecraftTime) world.setTime(minecraftTime);
        }
        lastMinecraftTime = minecraftTime;
    }

    private boolean isConfiguredWorld(String name) {
        List<String> worlds = getConfig().getStringList("worlds");
        return worlds.isEmpty() || worlds.stream().anyMatch(w -> w.equalsIgnoreCase(name));
    }

    private boolean isDay(LocalTime time, SunWindow sun) {
        return !time.isBefore(sun.sunrise) && time.isBefore(sun.sunset);
    }

    private long minecraftTimeFor(LocalTime time, SunWindow sun) {
        int now = time.toSecondOfDay();
        int rise = sun.sunrise.toSecondOfDay();
        int set = sun.sunset.toSecondOfDay();
        int dayLength = (set - rise + 86400) % 86400;
        if (dayLength == 0) dayLength = 43200;
        if (isDay(time, sun)) {
            double progress = (double)(now - rise) / dayLength;
            return Math.floorMod(Math.round(progress * 12000.0), 24000);
        }
        int nightLength = 86400 - dayLength;
        int elapsed = (now - set + 86400) % 86400;
        double progress = (double) elapsed / nightLength;
        return Math.floorMod(12000L + Math.round(progress * 12000.0), 24000);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (!blockSleep || !isConfiguredWorld(event.getPlayer().getWorld().getName())) return;
        ZonedDateTime now = ZonedDateTime.now(zone);
        SunWindow sun = mode.equals("REAL") ? SunriseSunset.calculate(now.toLocalDate(), latitude, longitude, zone) : new SunWindow(fixedSunrise, fixedSunset);
        if (!isDay(now.toLocalTime(), sun)) {
            event.setCancelled(true);
            String msg = getConfig().getString("messages.sleep-blocked", "&cYou cannot sleep while it is night in real life.");
            event.getPlayer().sendMessage(color(msg));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!blockPlayerTimeCommands) return;
        String command = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (command.equals("/time") || command.startsWith("/time ") || command.equals("/minecraft:time") || command.startsWith("/minecraft:time ")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&cTime is controlled by RealIndiaTime."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (!blockPlayerTimeCommands) return;
        String command = event.getCommand().trim().toLowerCase(Locale.ROOT);
        if (command.equals("time") || command.startsWith("time ") || command.equals("minecraft:time") || command.startsWith("minecraft:time ")) {
            event.setCancelled(true);
            event.getSender().sendMessage(color("&cTime is controlled by RealIndiaTime."));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("rit")) return false;
        if (!sender.hasPermission("realindiatime.admin")) {
            sender.sendMessage(color("&cNo permission."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage(color(getConfig().getString("messages.reloaded", "&aRealIndiaTime configuration reloaded.")));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("sync")) {
            syncAllWorlds();
            sender.sendMessage(color(getConfig().getString("messages.synced", "&aReal-world time synchronized.")));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            ZonedDateTime now = ZonedDateTime.now(zone);
            SunWindow sun = mode.equals("REAL") ? SunriseSunset.calculate(now.toLocalDate(), latitude, longitude, zone) : new SunWindow(fixedSunrise, fixedSunset);
            String phase = isDay(now.toLocalTime(), sun) ? "DAY" : "NIGHT";
            String msg = getConfig().getString("messages.status", "&eRealIndiaTime &7| &fMode: &a%mode% &7| &fIndia time: &a%time% &7| &fPhase: &a%phase%")
                    .replace("%mode%", mode).replace("%time%", now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))).replace("%phase%", phase);
            sender.sendMessage(color(msg));
            return true;
        }
        sender.sendMessage(color("&e/rit reload &7- reload config\n&e/rit sync &7- sync now\n&e/rit status &7- show status"));
        return true;
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    static record SunWindow(LocalTime sunrise, LocalTime sunset) {}
}
