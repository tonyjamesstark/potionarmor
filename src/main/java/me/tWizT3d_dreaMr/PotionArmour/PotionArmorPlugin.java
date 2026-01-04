/* (C)2024 */
package me.tWizT3d_dreaMr.PotionArmour;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

@SuppressWarnings("deprecation")
public class PotionArmorPlugin extends org.bukkit.plugin.java.JavaPlugin {
    public Logger logger = getLogger();
    private final Level DEFAULT_LOGLEVEL = Level.WARNING;
    private Level loglevel = DEFAULT_LOGLEVEL;
    public static PotionArmorPlugin plugin;
    public FileConfiguration config;
    public FileConfiguration lang;
    private File config_dir;
    private File effects_dir;
    public EventListener listener;
    public EffectManager manager;

    // Task ID for the periodic validation task
    private int validationTaskId = -1;

    // Validation configuration (loaded from config.yml)
    private boolean validationEnabled = true;
    private long validationIntervalTicks = 1200L; // Default 60s

    @Override
    public void onEnable() {
        plugin = this;
        EffectManager.setSupportedEffects();
        this.config_dir = getDataFolder();
        this.effects_dir = new File(config_dir, "effects");
        if (!effects_dir.exists()) {
            effects_dir.mkdir();
        }
        reloadConfigs(false);

        this.manager = new EffectManager(this);
        this.listener = new EventListener(manager);

        // load effects later so PlayerParticles has a chance to populate its lookup
        // tables
        Runnable job =
                () -> {
                    int loaded = manager.loadEffects(config);

                    // Load effects from additional files in effects/ directory
                    loaded += loadAdditionalEffects();

                    logger.info(loaded + " effects loaded");

                    // Start the periodic validation task
                    startValidationTask();
                };
        Bukkit.getScheduler().runTask(this, job);

        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    /**
     * Start the periodic validation task that catches edge cases
     * where effects might persist incorrectly.
     */
    private void startValidationTask() {
        // Cancel existing task if running
        if (validationTaskId != -1) {
            Bukkit.getScheduler().cancelTask(validationTaskId);
            validationTaskId = -1;
        }

        if (!validationEnabled) {
            logger.info("Periodic validation is disabled in config");
            return;
        }

        validationTaskId =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                this,
                                () -> {
                                    for (Player p : Bukkit.getOnlinePlayers()) {
                                        manager.validateAndFixPlayerEffects(p);
                                    }
                                },
                                validationIntervalTicks,
                                validationIntervalTicks)
                        .getTaskId();
        logger.info(
                "Started periodic effect validation task (every "
                        + (validationIntervalTicks / 20)
                        + " seconds)");
    }

    @Override
    public void onDisable() {
        this.saveConfig(); // in case loaded default configs

        // Cancel the validation task
        if (validationTaskId != -1) {
            Bukkit.getScheduler().cancelTask(validationTaskId);
            validationTaskId = -1;
        }
    }

    @Override
    public void saveConfig() {
        String lang_location = "NOT_FOUND";
        try {
            List<String> comments = new ArrayList<>(this.config.getComments("SupportedEffects"));

            // ensure effects list in comments
            if (!comments.contains(EffectManager.supportedEffects.get(0).toString())) {
                comments.add(lang.getString("supported_effects"));
                for (NamespacedKey k : EffectManager.supportedEffects) {
                    comments.add(k.toString());
                }
                this.config.setComments("SupportedEffects", comments);
            }
            super.saveConfig();
            this.config = getConfig();

            File lang_loc = new File(config_dir, config.getString("language_loc", "language.yml"));
            lang_location = lang_loc.toString();
            this.lang.save(lang_loc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, lang.getString("failed_save_file_exists") + lang_location);
        } catch (Exception e) {
            logger.log(
                    Level.SEVERE,
                    lang == null
                            ? "error saving config, language.yml not loaded"
                            : lang.getString("failed_save"));
        }
    }

    /**
     * {@code checkPerms} defaults to
     * {@link PotionArmorPlugin#checkPerms(CommandSender, String, boolean)}
     *
     * @see PotionArmorPlugin#checkPerms(CommandSender, String, boolean)
     */
    public boolean checkPerms(CommandSender p, String perm) {
        return checkPerms(p, perm, true); // default to check only players
    }

    /**
     * Check permissions
     *
     * @param p           who to check permission for
     * @param perm        the permission string
     * @param onlyPlayers whether to ignore (return true for) nonplayers
     * @return whether 'p' has permission 'perm'
     */
    public boolean checkPerms(CommandSender p, String perm, boolean onlyPlayers) {
        if (onlyPlayers && (!(p instanceof Player))) {
            return false;
        }
        if (p.hasPermission(perm)) {
            return true;
        } else {
            p.sendMessage(lang.getString("no_perms"));
            return false;
        }
    }

    public boolean reloadConfigs() {
        return reloadConfigs(true);
    }

    public boolean reloadConfigs(CommandSender sender) {
        return reloadConfigs(sender, true);
    }

    public boolean reloadConfigs(boolean isSetup) {
        // note: void reloadConfig() (no 's') is a superclass method, don't get confused
        return reloadConfigs((CommandSender) Bukkit.getConsoleSender(), isSetup);
    }

    public boolean reloadConfigs(CommandSender sender, boolean isSetup) {
        boolean saveNeeded = false;
        if (!checkPerms(sender, "Potionarmor.reload", false)) {
            String msg = "&cNo permission";
            if (lang != null) {
                msg = lang.getString("no_perms");
            }
            sender.sendMessage(msg);
            return true;
        }

        if (!(config_dir.exists() && config_dir.isDirectory())) {
            sender.sendMessage("Configuration directory not found, writing default");
            config_dir.delete();
            config_dir.mkdir();
            saveDefaultConfig();
            saveNeeded = true;
        }

        reloadConfig();
        this.config = getConfig();

        if (this.config == null) {
            this.saveConfig(); // will not overwrite existing, defaults to embedded
            this.config = getConfig();
        }

        try {
            loglevel = Level.parse(this.config.getString("meta.log_level"));
        } catch (IllegalArgumentException e) {
            logger.severe("could not parse meta.log_level");
            loglevel = DEFAULT_LOGLEVEL;
        }

        this.logger.setLevel(loglevel);
        logger.info("Logging level set to " + loglevel.getName());

        // Load validation configuration
        ConfigurationSection validationSection =
                this.config.getConfigurationSection("meta.validation");
        if (validationSection != null) {
            validationEnabled = validationSection.getBoolean("enabled", true);

            int intervalSecs = validationSection.getInt("interval_seconds", 60);
            if (intervalSecs < 30) {
                logger.warning(
                        "Validation interval too low ("
                                + intervalSecs
                                + "s), using minimum of 30s");
                intervalSecs = 30;
            } else if (intervalSecs > 300) {
                logger.warning(
                        "Validation interval too high ("
                                + intervalSecs
                                + "s), using maximum of 300s");
                intervalSecs = 300;
            }
            validationIntervalTicks = intervalSecs * 20L;

            logger.info(
                    "Validation settings: enabled="
                            + validationEnabled
                            + ", interval="
                            + (validationIntervalTicks / 20)
                            + "s");
        } else {
            logger.info("No validation config section found, using defaults");
        }

        EffectManager.setSupportedEffects();

        // TODO: check version and convert to new format

        File lang_loc = new File(config_dir, this.config.getString("meta.language_loc"));
        if (!lang_loc.exists()) {
            saveResource("language.yml", true); // overwrites
        }
        this.lang = YamlConfiguration.loadConfiguration(lang_loc);

        if (isSetup) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                manager.resetPlayerEffects(p);
            }
        }
        if (saveNeeded) {
            this.saveConfig();
        }
        if (isSetup) {
            this.manager.resetLoreCache();
            int loadedEffects = this.manager.loadEffects(this.config);
            loadedEffects += loadAdditionalEffects();
            logger.log(Level.INFO, "Loaded " + loadedEffects + " effects.");
        }

        sender.sendMessage(
                ChatColor.GREEN + "[potionarmor] " + this.lang.getString("config_reload"));
        return true;
    }

    /**
     * Load effects from additional .yml files in the effects/ directory.
     *
     * @return the total number of effects loaded from additional files
     */
    private int loadAdditionalEffects() {
        int totalLoaded = 0;

        if (!effects_dir.exists() || !effects_dir.isDirectory()) {
            return 0;
        }

        File[] files = effects_dir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        for (File file : files) {
            try {
                FileConfiguration effectConfig = YamlConfiguration.loadConfiguration(file);
                if (effectConfig.contains("Effects")) {
                    int loaded = manager.loadEffects(effectConfig);
                    logger.info("Loaded " + loaded + " effects from " + file.getName());
                    totalLoaded += loaded;
                }
            } catch (Exception e) {
                logger.warning(
                        "Failed to load effects from " + file.getName() + ": " + e.getMessage());
            }
        }

        return totalLoaded;
    }

    public boolean resetPlayer(CommandSender sender, String[] args) {
        logger.info("executing resetPlayer()...");

        // Allow players to reset themselves without args
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(lang.getString("invalid_command") + " Usage: /pareset <player>");
                return true;
            }
            // Self-reset - requires basic permission
            if (!checkPerms(sender, "Potionarmor.pareset.self", true)) return true;

            Player self = (Player) sender;
            manager.resetPlayerEffects(self);
            sender.sendMessage(
                    ChatColor.GREEN
                            + "[PotionArmor] "
                            + lang.getString("reset_notice")
                            + self.getName());
            return true;
        }

        // Resetting another player requires full permission
        if (!checkPerms(sender, "Potionarmor.pareset", false)) return true;

        if (args.length != 1) {
            sender.sendMessage(lang.getString("invalid_command"));
            return true;
        }
        logger.info("creating player profile..." + Arrays.asList(args));

        // TODO: does this take too long based on our offline player list...?
        // alternatively loop through online players, or try-catch creating online
        // player
        // TODO: fuzzy searching functionality was removed, consider re-adding
        OfflinePlayer p =
                Bukkit.getOfflinePlayer(Bukkit.getServer().createProfile(args[0]).getUniqueId());
        logger.info("resetting player..." + p.toString());

        if (p.isOnline()) {
            manager.resetPlayerEffects(p.getPlayer());
            sender.sendMessage(
                    lang.getString("reset_notice")
                            + args[0]); // not sure if want to keep arg passing...
            return true;
        }
        logger.info("sending message...");

        sender.sendMessage(
                lang.getString("player_not_found")
                        + args[0]); // not sure if want to keep arg passing...
        return true;
    }

    public boolean printEffects(CommandSender sender) {
        if (!checkPerms(sender, "Potionarmor.effect")) return true;

        String msg = lang.getString("supported_effects");
        for (NamespacedKey k : EffectManager.supportedEffects) {
            msg += k.toString() + ", ";
        }
        msg = msg.substring(0, msg.length() - 2); // clip final comma
        sender.sendMessage(msg);
        return true;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();
        switch (commandName) {
            case "pareset":
                return resetPlayer(sender, args);
            case "reload":
                return reloadConfigs(sender);
            case "effects":
                return printEffects(sender);
            case "debugdump":
                return dumpManager(sender);
            case "debugvalidation":
                return debugValidation(sender, args);
            default:
                break;
        }
        sender.sendMessage(lang.getString("invalid_command"));
        return false;
    }

    public boolean dumpManager(CommandSender sender) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("Command only supported from console.");
            return true;
        }
        manager.dump();
        return true;
    }

    /**
     * Debug validation state for a player.
     * Usage: /debugValidation [player] [validate]
     */
    public boolean debugValidation(CommandSender sender, String[] args) {
        if (!checkPerms(sender, "Potionarmor.debug", false)) return true;

        Player target = null;
        boolean runValidation = false;

        // Parse arguments
        if (args.length == 0) {
            // No args - show self if player, otherwise show usage
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage("Usage: /debugValidation <player> [validate]");
                return true;
            }
        } else {
            // First arg is player name
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found: " + args[0]);
                return true;
            }

            // Second arg is optional "validate" flag
            if (args.length > 1 && args[1].equalsIgnoreCase("validate")) {
                runValidation = true;
            }
        }

        // Get debug info
        String debugInfo = manager.getValidationDebugInfo(target);

        // Run validation if requested
        if (runValidation) {
            sender.sendMessage("Running manual validation for " + target.getName() + "...");
            boolean corrected = manager.validateAndFixPlayerEffects(target);
            sender.sendMessage("Validation complete. Corrections made: " + corrected);
            sender.sendMessage(""); // blank line
            // Get updated debug info after validation
            debugInfo = manager.getValidationDebugInfo(target);
        }

        // Send debug info
        sender.sendMessage("=== Validation Debug ===");
        for (String line : debugInfo.split("\n")) {
            sender.sendMessage(line);
        }
        sender.sendMessage("========================");

        return true;
    }
}
