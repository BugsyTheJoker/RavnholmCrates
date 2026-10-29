
package dk.ravnholm.crates;

import dk.ravnholm.crates.commands.CratesCommand;
import dk.ravnholm.crates.crates.Crate;
import dk.ravnholm.crates.crates.CrateManager;
import dk.ravnholm.crates.holograms.CrateHologramManager;
import dk.ravnholm.crates.keys.KeyManager;
import dk.ravnholm.crates.listeners.CrateGUIListener;
import dk.ravnholm.crates.listeners.CrateInteractListener;
import dk.ravnholm.crates.rewards.Reward;
import dk.ravnholm.crates.rewards.RewardManager;
import dk.ravnholm.crates.statistics.CrateStatisticsManager;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class RavnholmCrates extends JavaPlugin {

    private CrateManager crateManager;
    private KeyManager keyManager;
    private RewardManager rewardManager;
    private CrateStatisticsManager statisticsManager;
    private CrateHologramManager hologramManager;
    private CrateInteractListener crateInteractListener;

    private BukkitTask statisticsSaveTask;
    private BukkitTask hologramSyncTask;

    private static final long STATISTICS_SAVE_INTERVAL = 6000L;
    private static final long HOLOGRAM_SYNC_INTERVAL = 600L;

    // =====================================================
    // PLUGIN START
    // =====================================================

    @Override
    public void onEnable() {
        saveDefaultConfig();

        keyManager = new KeyManager(this);

        File crateTypesFile = new File(
                getDataFolder(),
                "crate-types.yml"
        );

        if (!crateTypesFile.exists()) {
            saveResource("crate-types.yml", false);
        }

        if (!reloadCrateTypes()) {
            getLogger().severe(
                    "Ingen gyldig crate-konfiguration kunne indlæses!"
            );

            crateManager = new CrateManager();
            rewardManager = new RewardManager();

            crateManager.loadLocations(this);

            getLogger().warning(
                    "Crate-typerne er ikke aktive. " +
                            "Gemte placeringer er forsøgt bevaret."
            );
        }

        statisticsManager = new CrateStatisticsManager(this);

        startStatisticsAutoSave();

        crateInteractListener = new CrateInteractListener(this);

        getServer().getPluginManager().registerEvents(
                crateInteractListener,
                this
        );

        getServer().getPluginManager().registerEvents(
                new CrateGUIListener(),
                this
        );

        Objects.requireNonNull(
                getCommand("ravnholmcrates")
        ).setExecutor(
                new CratesCommand(this)
        );

        initializeHolograms();

        getLogger().info("RavnholmCrates er startet!");
    }

    // =====================================================
    // AUTOMATISK GEMNING AF STATISTIK
    // =====================================================

    private void startStatisticsAutoSave() {
        if (statisticsSaveTask != null) {
            statisticsSaveTask.cancel();
        }

        statisticsSaveTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> {
                    if (statisticsManager != null
                            && !statisticsManager.save()) {

                        getLogger().warning(
                                "Automatisk lagring af crate-statistik fejlede!"
                        );
                    }
                },
                STATISTICS_SAVE_INTERVAL,
                STATISTICS_SAVE_INTERVAL
        );
    }

    // =====================================================
    // FANCYHOLOGRAMS
    // =====================================================

    private void initializeHolograms() {
        if (hologramSyncTask != null) {
            hologramSyncTask.cancel();
            hologramSyncTask = null;
        }

        if (!getServer().getPluginManager().isPluginEnabled(
                "FancyHolograms"
        )) {
            hologramManager = null;

            getLogger().info(
                    "FancyHolograms er ikke installeret. " +
                            "Fortsætter uden hologrammer."
            );

            return;
        }

        try {
            hologramManager = new CrateHologramManager(this);

        } catch (RuntimeException | LinkageError exception) {
            hologramManager = null;

            getLogger().severe(
                    "Kunne ikke initialisere FancyHolograms: "
                            + exception.getMessage()
            );

            return;
        }

        if (!hologramManager.isEnabled()) {
            return;
        }

        hologramSyncTask = getServer().getScheduler().runTaskTimer(
                this,
                this::refreshCrateHolograms,
                20L,
                HOLOGRAM_SYNC_INTERVAL
        );
    }

    public void refreshCrateHolograms() {
        if (hologramManager == null
                || !hologramManager.isEnabled()
                || crateManager == null) {
            return;
        }

        if (!getServer().isPrimaryThread()) {
            getServer().getScheduler().runTask(
                    this,
                    this::refreshCrateHolograms
            );
            return;
        }

        try {
            hologramManager.syncAll(
                    crateManager.getCrateLocations()
            );

        } catch (RuntimeException | LinkageError exception) {
            getLogger().warning(
                    "Kunne ikke synkronisere crate-hologrammer: "
                            + exception.getMessage()
            );
        }
    }

    // =====================================================
    // PLUGIN STOP
    // =====================================================

    @Override
    public void onDisable() {
        if (crateInteractListener != null) {
            crateInteractListener.shutdown();
            crateInteractListener = null;
        }

        if (statisticsSaveTask != null) {
            statisticsSaveTask.cancel();
            statisticsSaveTask = null;
        }

        if (hologramSyncTask != null) {
            hologramSyncTask.cancel();
            hologramSyncTask = null;
        }

        if (hologramManager != null) {
            try {
                hologramManager.shutdown();

            } catch (RuntimeException | LinkageError exception) {
                getLogger().warning(
                        "Fejl ved lukning af hologrammer: "
                                + exception.getMessage()
                );
            }

            hologramManager = null;
        }

        getServer().getScheduler().cancelTasks(this);

        if (crateManager != null
                && !crateManager.saveLocations(this)) {

            getLogger().severe(
                    "Fysiske crate-placeringer kunne ikke gemmes!"
            );
        }

        if (statisticsManager != null
                && !statisticsManager.save()) {

            getLogger().warning(
                    "Crate-statistik kunne ikke gemmes!"
            );
        }

        getLogger().info("RavnholmCrates er stoppet!");
    }

    // =====================================================
    // GENINDLÆSNING AF CRATE-TYPER
    // =====================================================

    public boolean reloadCrateTypes() {
        CrateManager newCrateManager = new CrateManager();
        RewardManager newRewardManager = new RewardManager();

        int loadedCrates = loadCrateTypes(
                newCrateManager,
                newRewardManager
        );

        if (loadedCrates <= 0) {
            getLogger().severe(
                    "Genindlæsning afbrudt. " +
                            "Den tidligere konfiguration bevares."
            );
            return false;
        }

        if (crateManager != null) {
            if (!crateManager.wasLocationLoadSuccessful()) {
                getLogger().warning(
                        "Eksisterende placeringer kunne ikke indlæses; " +
                                "forsøger crates.yml igen."
                );

            } else if (!crateManager.saveLocations(this)) {
                getLogger().severe(
                        "Genindlæsning afbrudt: " +
                                "placeringer kunne ikke gemmes."
                );
                return false;
            }
        }

        newCrateManager.loadLocations(this);

        if (!newCrateManager.wasLocationLoadSuccessful()) {
            getLogger().severe(
                    "Genindlæsning afbrudt: " +
                            "crates.yml kunne ikke indlæses."
            );
            return false;
        }

        crateManager = newCrateManager;
        rewardManager = newRewardManager;

        getLogger().info(
                "RavnholmCrates genindlæst: "
                        + loadedCrates
                        + " crate-typer."
        );

        if (hologramManager != null) {
            refreshCrateHolograms();
        }

        return true;
    }

    // =====================================================
    // INDLÆS CRATES OG REWARDS
    // =====================================================

    private int loadCrateTypes(
            CrateManager targetCrates,
            RewardManager targetRewards
    ) {
        File file = new File(
                getDataFolder(),
                "crate-types.yml"
        );

        if (!file.isFile()) {
            getLogger().severe(
                    "crate-types.yml findes ikke!"
            );
            return -1;
        }

        YamlConfiguration config = new YamlConfiguration();

        try {
            config.load(file);

        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().severe(
                    "Kunne ikke indlæse crate-types.yml: "
                            + exception.getMessage()
            );
            return -1;
        }

        ConfigurationSection cratesSection =
                config.getConfigurationSection("crates");

        if (cratesSection == null
                || cratesSection.getKeys(false).isEmpty()) {

            getLogger().severe(
                    "Ingen crate-typer fundet i crate-types.yml."
            );
            return -1;
        }

        Set<String> definedCrateIds = new HashSet<>();

        boolean errors = false;
        int loadedCrates = 0;
        int loadedRewards = 0;

        // Registrer alle crate-ID'er til validering af KEY.
        for (String id : cratesSection.getKeys(false)) {
            String normalized = normalizeId(id);

            if (normalized == null
                    || !definedCrateIds.add(normalized)) {

                getLogger().severe(
                        "Tomt eller dubleret crate-ID: " + id
                );

                errors = true;
            }
        }

        if (errors) {
            return -1;
        }

        for (String crateId : cratesSection.getKeys(false)) {

            String normalizedCrateId = normalizeId(crateId);

            ConfigurationSection crateSection =
                    cratesSection.getConfigurationSection(crateId);

            if (crateSection == null) {
                getLogger().severe(
                        "Ugyldig crate-sektion: " + crateId
                );
                errors = true;
                continue;
            }

            ConfigurationSection rewardsSection =
                    crateSection.getConfigurationSection("rewards");

            if (rewardsSection == null
                    || rewardsSection.getKeys(false).isEmpty()) {

                getLogger().severe(
                        "Crate '" + crateId
                                + "' mangler belønninger."
                );

                errors = true;
                continue;
            }

            List<Reward> validRewards = new ArrayList<>();
            Set<String> rewardIds = new HashSet<>();

            for (String rewardId : rewardsSection.getKeys(false)) {

                String normalizedRewardId =
                        normalizeId(rewardId);

                if (normalizedRewardId == null
                        || !rewardIds.add(normalizedRewardId)) {

                    getLogger().severe(
                            "Tomt eller dubleret reward-ID i '"
                                    + crateId + "': " + rewardId
                    );

                    errors = true;
                    continue;
                }

                ConfigurationSection rewardSection =
                        rewardsSection.getConfigurationSection(rewardId);

                if (rewardSection == null) {
                    getLogger().severe(
                            "Ugyldig reward-sektion: "
                                    + crateId + "." + rewardId
                    );

                    errors = true;
                    continue;
                }

                try {
                    Reward reward = parseReward(
                            rewardId,
                            rewardSection
                    );

                    if (reward.isKeyReward()
                            && !definedCrateIds.contains(
                            reward.getKeyCrateId()
                    )) {

                        throw new IllegalArgumentException(
                                "Ukendt mål-crate '"
                                        + reward.getKeyCrateId()
                                        + "' for nøglepræmien."
                        );
                    }

                    validRewards.add(reward);

                } catch (IllegalArgumentException
                         | IllegalStateException exception) {

                    getLogger().severe(
                            "Ugyldig reward '"
                                    + crateId + "." + rewardId
                                    + "': " + exception.getMessage()
                    );

                    errors = true;
                }
            }

            if (validRewards.isEmpty()) {
                getLogger().severe(
                        "Crate '" + crateId
                                + "' har ingen gyldige belønninger."
                );

                errors = true;
                continue;
            }

            long totalWeight = 0;

            try {
                for (Reward reward : validRewards) {
                    totalWeight = Math.addExact(
                            totalWeight,
                            reward.getWeightUnits()
                    );
                }

            } catch (ArithmeticException exception) {
                getLogger().severe(
                        "Samlet vægt er for stor i crate '"
                                + crateId + "'."
                );

                errors = true;
                continue;
            }

            if (totalWeight <= 0) {
                getLogger().severe(
                        "Samlet vægt er ugyldig i crate '"
                                + crateId + "'."
                );

                errors = true;
                continue;
            }

            String crateName = colorize(
                    crateSection.getString("name", crateId)
            );

            targetCrates.registerCrate(
                    new Crate(normalizedCrateId, crateName)
            );

            for (Reward reward : validRewards) {
                targetRewards.registerReward(
                        normalizedCrateId,
                        reward
                );
            }

            if (targetRewards.getRewards(normalizedCrateId).size()
                    != validRewards.size()) {

                getLogger().severe(
                        "Kunne ikke registrere alle rewards i crate '"
                                + crateId + "'."
                );

                errors = true;
                continue;
            }

            loadedCrates++;
            loadedRewards += validRewards.size();

            getLogger().info(
                    "Validerede crate '"
                            + crateId
                            + "' med "
                            + validRewards.size()
                            + " belønninger."
            );
        }

        if (errors
                || loadedCrates == 0
                || loadedRewards == 0) {

            getLogger().severe(
                    "Fejl i crate-types.yml. " +
                            "Ingen ændringer aktiveres."
            );

            return -1;
        }

        getLogger().info(
                "Validerede "
                        + loadedCrates
                        + " crates og "
                        + loadedRewards
                        + " belønninger."
        );

        return loadedCrates;
    }

    // =====================================================
    // PARSE REWARD: ITEM, MONEY ELLER KEY
    // =====================================================

    private Reward parseReward(
            String id,
            ConfigurationSection section
    ) {
        String name = colorize(
                section.getString("name", id)
        );

        String rawType = section.getString(
                "type",
                "ITEM"
        );

        if (rawType == null) {
            throw new IllegalArgumentException(
                    "Reward-type mangler."
            );
        }

        Reward.RewardType type;

        try {
            type = Reward.RewardType.valueOf(
                    rawType.trim().toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Ukendt reward-type: " + rawType
            );
        }

        double weight = readPositiveNumber(
                section,
                "weight"
        );

        Material material = readMaterial(
                section,
                type
        );

        int amount;

        if (type == Reward.RewardType.MONEY) {
            // Pengesummen er ikke antallet af display-items.
            amount = 1;

        } else if (type == Reward.RewardType.KEY) {
            // Antallet af nøgler håndteres separat.
            amount = 1;

        } else {
            amount = readPositiveInteger(
                    section,
                    "amount"
            );

            if (amount > material.getMaxStackSize()) {
                throw new IllegalArgumentException(
                        "Item-antal "
                                + amount
                                + " overstiger maksimal stack-size for "
                                + material
                                + "."
                );
            }
        }

        ItemStack displayItem = new ItemStack(
                material,
                amount
        );

        applyItemMetadata(
                displayItem,
                section,
                name
        );

        return switch (type) {

            case ITEM -> Reward.item(
                    id,
                    name,
                    displayItem,
                    weight
            );

            case MONEY -> Reward.money(
                    id,
                    name,
                    displayItem,
                    readPositiveNumber(section, "amount"),
                    weight
            );

            case KEY -> {
                String target = normalizeId(
                        section.getString("crate")
                );

                if (target == null) {
                    throw new IllegalArgumentException(
                            "KEY mangler 'crate' (mål-crate-ID)."
                    );
                }

                int keyAmount = readPositiveInteger(
                        section,
                        "amount"
                );

                yield Reward.key(
                        id,
                        name,
                        displayItem,
                        target,
                        keyAmount,
                        weight
                );
            }
        };
    }

    // =====================================================
    // MATERIALER
    // =====================================================

    private Material readMaterial(
            ConfigurationSection section,
            Reward.RewardType type
    ) {
        String defaultMaterial = switch (type) {
            case ITEM -> "";
            case MONEY -> "SUNFLOWER";
            case KEY -> "TRIPWIRE_HOOK";
        };

        String raw = section.getString(
                "material",
                defaultMaterial
        );

        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "Materiale mangler."
            );
        }

        Material material = Material.matchMaterial(
                raw.trim().toUpperCase(Locale.ROOT)
        );

        if (material == null
                || material.isAir()
                || !material.isItem()) {

            throw new IllegalArgumentException(
                    "Ugyldigt materiale: " + raw
            );
        }

        return material;
    }

    // =====================================================
    // VALIDERING AF TAL
    // =====================================================

    private double readPositiveNumber(
            ConfigurationSection section,
            String field
    ) {
        Object value = section.get(field);

        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "'" + field
                            + "' skal være et tal (uden citationstegn)."
            );
        }

        double result = number.doubleValue();

        if (!Double.isFinite(result) || result <= 0) {
            throw new IllegalArgumentException(
                    "'" + field
                            + "' skal være større end 0."
            );
        }

        return result;
    }

    private int readPositiveInteger(
            ConfigurationSection section,
            String field
    ) {
        Object value = section.get(field);

        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "'" + field + "' skal være et heltal."
            );
        }

        double result = number.doubleValue();

        if (!Double.isFinite(result)
                || result < 1
                || result > Integer.MAX_VALUE
                || result != Math.rint(result)) {

            throw new IllegalArgumentException(
                    "'" + field
                            + "' skal være et positivt heltal."
            );
        }

        return (int) result;
    }

    // =====================================================
    // ITEM-METADATA
    // NAVN, LORE, UNBREAKABLE OG ENCHANTMENTS
    // =====================================================

    private void applyItemMetadata(
            ItemStack item,
            ConfigurationSection section,
            String rewardName
    ) {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            throw new IllegalArgumentException(
                    "Materialet understøtter ikke item-metadata."
            );
        }

        // Præmiens navn bruges også på genstanden,
        // medmindre 'item-name' er angivet.
        meta.setDisplayName(
                colorize(
                        section.getString(
                                "item-name",
                                rewardName
                        )
                )
        );

        // -------------------------------------------------
        // LORE
        // -------------------------------------------------

        if (section.contains("lore")) {

            if (!section.isList("lore")) {
                throw new IllegalArgumentException(
                        "'lore' skal være en YAML-liste."
                );
            }

            List<String> lore = new ArrayList<>();

            for (Object line : section.getList(
                    "lore",
                    List.of()
            )) {
                if (!(line instanceof String text)) {
                    throw new IllegalArgumentException(
                            "Alle lore-linjer skal være tekst."
                    );
                }

                lore.add(colorize(text));
            }

            meta.setLore(lore);
        }

        // -------------------------------------------------
        // UNBREAKABLE
        // -------------------------------------------------

        if (section.contains("unbreakable")) {

            if (!section.isBoolean("unbreakable")) {
                throw new IllegalArgumentException(
                        "'unbreakable' skal være true eller false."
                );
            }

            meta.setUnbreakable(
                    section.getBoolean("unbreakable")
            );
        }

        // -------------------------------------------------
        // ENCHANTMENTS
        // -------------------------------------------------

        ConfigurationSection enchantments =
                section.getConfigurationSection("enchantments");

        if (section.contains("enchantments")
                && enchantments == null) {

            throw new IllegalArgumentException(
                    "'enchantments' skal være en YAML-sektion."
            );
        }

        if (enchantments != null) {

            for (String enchantName
                    : enchantments.getKeys(false)) {

                Enchantment enchantment =
                        findEnchantment(enchantName);

                if (enchantment == null) {
                    throw new IllegalArgumentException(
                            "Ukendt enchantment: " + enchantName
                    );
                }

                int level = readPositiveInteger(
                        enchantments,
                        enchantName
                );

                if (level > Short.MAX_VALUE) {
                    throw new IllegalArgumentException(
                            "Enchantment-level er for højt: " + level
                    );
                }

                // -----------------------------------------
                // ENCHANTED BOOK
                // -----------------------------------------

                if (meta instanceof EnchantmentStorageMeta bookMeta) {

                    if (!bookMeta.addStoredEnchant(
                            enchantment,
                            level,
                            true
                    )) {
                        throw new IllegalArgumentException(
                                "Kunne ikke tilføje stored enchantment: "
                                        + enchantName
                        );
                    }

                } else {

                    // -------------------------------------
                    // ALMINDELIGE ITEMS
                    // -------------------------------------

                    if (!meta.addEnchant(
                            enchantment,
                            level,
                            true
                    )) {
                        throw new IllegalArgumentException(
                                "Kunne ikke tilføje enchantment: "
                                        + enchantName
                        );
                    }
                }
            }
        }

        // Gem alle ændringer på genstanden.
        item.setItemMeta(meta);
    }

    // =====================================================
    // FIND ENCHANTMENT
    // =====================================================

    private Enchantment findEnchantment(String name) {

        String id = name
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');

        id = switch (id) {
            case "damage_all" -> "sharpness";
            case "dig_speed" -> "efficiency";
            case "durability" -> "unbreaking";
            case "loot_bonus_blocks" -> "fortune";
            case "loot_bonus_mobs" -> "looting";
            case "protection_environmental" -> "protection";
            case "arrow_damage" -> "power";
            case "arrow_fire" -> "flame";
            case "arrow_infinite" -> "infinity";
            default -> id;
        };

        NamespacedKey key = NamespacedKey.fromString(id);

        return key == null
                ? null
                : Registry.ENCHANTMENT.get(key);
    }

    // =====================================================
    // HJÆLPEMETODER
    // =====================================================

    private String normalizeId(String id) {
        return id == null || id.isBlank()
                ? null
                : id.trim().toLowerCase(Locale.ROOT);
    }

    private String colorize(String text) {
        return text == null
                ? ""
                : text.replace('&', '§');
    }

    // =====================================================
    // GETTERS
    // =====================================================

    public CrateManager getCrateManager() {
        return crateManager;
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public CrateStatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    public CrateHologramManager getHologramManager() {
        return hologramManager;
    }
}
