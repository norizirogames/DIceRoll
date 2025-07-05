package com.example.diceroll;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DicerollPlugin extends JavaPlugin {

    private final Random random = new Random();
    private final Pattern dicePattern = Pattern.compile("^(\\d*)d(\\d+)$");
    private Map<UUID, Long> cooldowns = new HashMap<>();
    private long cooldownSeconds;
    private String publicFormat;
    private String secretFormat;
    private String consoleFormat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        getLogger().info("Diceroll Plugin enabled!");
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        cooldownSeconds = config.getLong("cooldown", 0);
        publicFormat = config.getString("public-format", "%player% [%dice%] 結果: %rolls% 合計: %total%");
        secretFormat = config.getString("secret-format", "[シークレット] %player% [%dice%] 結果: %rolls% 合計: %total%");
        consoleFormat = config.getString("console-format", "[CONSOLE] [%dice%] 結果: %rolls% 合計: %total%");
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        loadConfigValues();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        boolean isSecret = cmd.equals("sd") || cmd.equals("sd100");
        String input;

        // d100コマンドは1d100固定、sd100コマンドは1d100固定
        if (cmd.equals("d100") || cmd.equals("sd100")) {
            input = "1d100";
        } else if (args.length == 0 && label.matches("^(sd|d)\\d+$")) {
            input = "1d" + label.replaceAll("^(sd|d)", "");
        } else if (args.length == 1) {
            input = args[0];
        } else {
            sender.sendMessage("使い方: /d 1d100 または /d100, /sd 1d100 または /sd100, /d100, /sd100");
            return true;
        }

        if (input.matches("^\\d+$")) {
            input = "1d" + input;
        }

        Matcher matcher = dicePattern.matcher(input);
        if (!matcher.matches()) {
            sender.sendMessage("使い方: /d 1d100 または /d100, /sd 1d100 または /sd100, /d100, /sd100");
            return true;
        }

        int count = matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
        int sides = Integer.parseInt(matcher.group(2));
        // 最大10d100に制限
        if (count < 1 || count > 10 || sides < 2 || sides > 100) {
            sender.sendMessage("ダイスの数は1〜10、面数は2〜100で指定してください。");
            return true;
        }

        // クールタイム判定（プレイヤーのみ）
        if (sender instanceof Player && cooldownSeconds > 0) {
            Player player = (Player) sender;
            long now = System.currentTimeMillis();
            long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < cooldownSeconds * 1000) {
                long remain = (cooldownSeconds * 1000 - (now - last)) / 1000;
                sender.sendMessage("クールタイム中です。あと " + remain + " 秒お待ちください。");
                return true;
            }
            cooldowns.put(player.getUniqueId(), now);
        }

        int total = 0;
        StringBuilder rolls = new StringBuilder();
        for (int i = 0; i < count; i++) {
            int roll = random.nextInt(sides) + 1;
            total += roll;
            rolls.append(roll);
            if (i < count - 1) rolls.append(", ");
        }

        String playerName = (sender instanceof Player) ? sender.getName() : "";
        String msg;
        if (sender instanceof Player) {
            if (isSecret) {
                msg = secretFormat;
            } else {
                msg = publicFormat;
            }
        } else {
            msg = consoleFormat;
        }
        msg = msg.replace("%player%", playerName)
                 .replace("%dice%", input)
                 .replace("%rolls%", rolls.toString())
                 .replace("%total%", String.valueOf(total));

        // シークレットは実行者のみ、それ以外は全員
        if (isSecret && sender instanceof Player) {
            sender.sendMessage(msg);
        } else if (sender instanceof Player) {
            Bukkit.getServer().broadcastMessage(msg);
        } else {
            // コンソールやコマンドブロック
            sender.sendMessage(msg);
        }
        return true;
    }
}