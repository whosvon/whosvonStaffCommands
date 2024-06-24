package com.whosvon.kits.whosvonstaffplus;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WhosvonStaffPlus extends JavaPlugin implements Listener {

    private final Map<String, Long> mutedPlayers = new HashMap<>();
    private final Set<String> frozenPlayers = new HashSet<>();
    private final Set<String> commandSpyPlayers = new HashSet<>();
    private final Map<String, BufferedWriter> playerLogWriters = new HashMap<>();
    private final Map<UUID, Set<UUID>> teleportHistory = new HashMap<>();

    private static final String STAFF_ITEM_RANDOM_TP = "RandomTP";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveConfig();
        closeAllLogWriters();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "staff":
                listStaffCommands(player);
                return true;
            case "tempban":
                tempBanPlayer(player, args);
                return true;
            case "unban":
                unbanPlayer(player, args);
                return true;
            case "mute":
                mutePlayer(player, args);
                return true;
            case "unmute":
                unmutePlayer(player, args);
                return true;
            case "freeze":
                freezePlayer(player, args);
                return true;
            case "unfreeze":
                unfreezePlayer(player, args);
                return true;
            case "commandspy":
                toggleCommandSpy(player, args);
                return true;
            case "staffmode":
                toggleStaffMode(player, args);
                return true;
            case "invsee":
                if (args.length < 1) {
                    player.sendMessage(ChatColor.RED + getConfig().getString("messages.invsee.usage"));
                } else {
                    openPlayerInventory(player, args[0]);
                }
                return true;
            default:
                return false;
        }
    }

    private void listStaffCommands(Player player) {
        player.sendMessage(ChatColor.GREEN + getConfig().getString("commands.staff.list"));
    }

    private void tempBanPlayer(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.tempban.usage"));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.tempban.not_found"));
            return;
        }

        long banDuration = parseDuration(args[1]);
        if (banDuration <= 0) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.tempban.invalid_duration"));
            return;
        }

        String reason = concatenateArgs(args, 2);
        Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), reason, new Date(System.currentTimeMillis() + banDuration), player.getName());
        target.kickPlayer(ChatColor.RED + getConfig().getString("messages.tempban.kick_message").replace("{reason}", reason));
    }

    private void unbanPlayer(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.unban.usage"));
            return;
        }

        String target = args[0];
        Bukkit.getBanList(BanList.Type.NAME).pardon(target);
        player.sendMessage(ChatColor.GREEN + getConfig().getString("messages.unban.success").replace("{player}", target));
    }

    private void mutePlayer(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.mute.usage"));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.mute.not_found"));
            return;
        }

        long muteDuration = parseDuration(args[1]);
        if (muteDuration <= 0) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.mute.invalid_duration"));
            return;
        }

        mutedPlayers.put(target.getName(), System.currentTimeMillis() + muteDuration);
        target.sendMessage(ChatColor.RED + getConfig().getString("messages.mute.mute_message").replace("{reason}", concatenateArgs(args, 2)));
    }

    private void unmutePlayer(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.unmute.usage"));
            return;
        }

        mutedPlayers.remove(args[0]);
        player.sendMessage(ChatColor.GREEN + getConfig().getString("messages.unmute.success").replace("{player}", args[0]));
    }

    private void freezePlayer(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.freeze.usage"));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.freeze.not_found"));
            return;
        }

        frozenPlayers.add(target.getName());
        target.sendMessage(ChatColor.BLUE + getConfig().getString("messages.freeze.freeze_message"));
    }

    private void unfreezePlayer(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.unfreeze.usage"));
            return;
        }

        frozenPlayers.remove(args[0]);
        player.sendMessage(ChatColor.GREEN + getConfig().getString("messages.unfreeze.success").replace("{player}", args[0]));
    }

    private void toggleCommandSpy(Player player, String[] args) {
        if (args.length < 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.commandspy.usage"));
            return;
        }

        if (args[0].equalsIgnoreCase("on")) {
            commandSpyPlayers.add(player.getName());
            player.sendMessage(ChatColor.GREEN + getConfig().getString("messages.commandspy.enabled"));
        } else {
            commandSpyPlayers.remove(player.getName());
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.commandspy.disabled"));
        }
    }

    private void toggleStaffMode(Player player, String[] args) {
        if (args.length < 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            player.sendMessage(ChatColor.RED + getConfig().getString("messages.staffmode.usage"));
            return;
        }

        if (args[0].equalsIgnoreCase("on")) {
            enableStaffMode(player);
        } else {
            disableStaffMode(player);
        }
    }

    private void enableStaffMode(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().addItem(createStaffItem(STAFF_ITEM_RANDOM_TP));
        player.sendMessage(ChatColor.GREEN + getConfig().getString("messages.staffmode.enabled"));
    }

    private void disableStaffMode(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        player.sendMessage(ChatColor.RED + getConfig().getString("messages.staffmode.disabled"));
    }

    private ItemStack createStaffItem(String name) {
        Material material;
        if (STAFF_ITEM_RANDOM_TP.equals(name)) {
            material = Material.ENDER_PEARL;
            ItemStack item = new ItemStack(material);
            item.getItemMeta().setDisplayName(ChatColor.GREEN + name);
            return item;
        }
        return null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        createLogWriter(player.getName());
    }

    @EventHandler
    public void onPlayerCommandConsole(PlayerCommandPreprocessEvent event) {
        String playerName = event.getPlayer().getName();
        String logMessage = ChatColor.GOLD + playerName + " executed command: " + event.getMessage();

        // Log the command to console
        getLogger().info(logMessage);
        logPlayerCommand(playerName, event.getMessage());
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (commandSpyPlayers.isEmpty()) return;

        String message = event.getMessage();
        String playerName = event.getPlayer().getName();
        String logMessage = ChatColor.GOLD + playerName + " executed command: " + message;

        for (String spy : commandSpyPlayers) {
            Player spyPlayer = Bukkit.getPlayer(spy);
            if (spyPlayer != null && spyPlayer.isOnline()) {
                spyPlayer.sendMessage(logMessage);
            }
        }

        getLogger().info(logMessage);
        logPlayerCommand(playerName, message);
    }


    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (mutedPlayers.containsKey(player.getName())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You are currently muted and cannot chat.");
        }
    }

    private void teleportRandomPlayer(Player player) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.remove(player);

        if (onlinePlayers.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No other players online to teleport to.");
            return;
        }

        Set<UUID> usedPlayers = teleportHistory.getOrDefault(player.getUniqueId(), new HashSet<>());

        if (usedPlayers.size() == onlinePlayers.size()) {
            usedPlayers.clear();
        }

        Player target;
        do {
            target = onlinePlayers.get(new Random().nextInt(onlinePlayers.size()));
        } while (usedPlayers.contains(target.getUniqueId()));

        usedPlayers.add(target.getUniqueId());
        teleportHistory.put(player.getUniqueId(), usedPlayers);

        player.teleport(target);
        player.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName());
    }

    private void openPlayerInventory(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }

        player.openInventory(target.getInventory());
        player.sendMessage(ChatColor.GREEN + "Opening inventory of " + target.getName());
    }

    private void createLogWriter(String playerName) {
        try {
            File logFile = new File(getDataFolder(), playerName + ".txt");
            if (!logFile.exists()) {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            playerLogWriters.put(playerName, writer);
        } catch (IOException e) {
            getLogger().severe("Failed to create log file for " + playerName + ": " + e.getMessage());
        }
    }

    private void closeLogWriter(String playerName) {
        BufferedWriter writer = playerLogWriters.remove(playerName);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                getLogger().severe("Failed to close log file for " + playerName + ": " + e.getMessage());
            }
        }
    }

    private void closeAllLogWriters() {
        for (BufferedWriter writer : playerLogWriters.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                getLogger().severe("Failed to close log file: " + e.getMessage());
            }
        }
        playerLogWriters.clear();
    }

    private void logPlayerCommand(String playerName, String command) {
        BufferedWriter writer = playerLogWriters.get(playerName);
        if (writer != null) {
            try {
                writer.write(command);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                getLogger().severe("Failed to write command to log file for " + playerName + ": " + e.getMessage());
            }
        }
    }


    private String concatenateArgs(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private long parseDuration(String input) {
        Pattern pattern = Pattern.compile("(\\d+)([dh])");
        Matcher matcher = pattern.matcher(input);
        long totalMilliseconds = 0;

        while (matcher.find()) {
            int amount = Integer.parseInt(matcher.group(1));
            char unit = matcher.group(2).charAt(0);

            switch (unit) {
                case 'd':
                    totalMilliseconds += amount * 24 * 60 * 60 * 1000; // days to milliseconds
                    break;
                case 'h':
                    totalMilliseconds += amount * 60 * 60 * 1000; // hours to milliseconds
                    break;
                default:
                    break;
            }
        }

        return totalMilliseconds;
    }
}
