
package dk.ravnholm.crates.statistics;

import dk.ravnholm.crates.RavnholmCrates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CrateStatisticsManager {

    private final RavnholmCrates plugin;
    private final File statisticsFile;

    // =====================================================
    // STATISTIK
    // =====================================================

    private long totalOpenings = 0;

    private final Map<String, Long> crateOpenings =
            new HashMap<>();

    private final Map<UUID, Long> playerOpenings =
            new HashMap<>();

    // Beskyt eksisterende data, hvis indlæsning fejler.
    private boolean loadedSuccessfully = false;

    // =====================================================
    // KONSTRUKTØR
    // =====================================================

    public CrateStatisticsManager(RavnholmCrates plugin) {

        this.plugin = plugin;

        this.statisticsFile = new File(
                plugin.getDataFolder(),
                "crate-stats.yml"
        );

        load();
    }

    // =====================================================
    // REGISTRER EN CRATE-ÅBNING
    // =====================================================

    public void recordOpening(
            UUID playerId,
            String crateId
    ) {

        if (!loadedSuccessfully) {
            return;
        }

        if (playerId == null
                || crateId == null
                || crateId.isBlank()) {

            return;
        }

        String normalizedId =
                crateId.toLowerCase(Locale.ROOT);

        // Samlet antal åbninger.
        totalOpenings = increment(totalOpenings);

        // Åbninger for denne crate-type.
        long currentCrateCount =
                crateOpenings.getOrDefault(
                        normalizedId,
                        0L
                );

        crateOpenings.put(
                normalizedId,
                increment(currentCrateCount)
        );

        // Åbninger for denne spiller.
        long currentPlayerCount =
                playerOpenings.getOrDefault(
                        playerId,
                        0L
                );

        playerOpenings.put(
                playerId,
                increment(currentPlayerCount)
        );
    }

    // =====================================================
    // HENT SAMLET ANTAL ÅBNINGER
    // =====================================================

    public long getTotalOpenings() {

        return totalOpenings;
    }

    // =====================================================
    // HENT ÅBNINGER FOR EN CRATE-TYPE
    // =====================================================

    public long getCrateOpenings(String crateId) {

        if (crateId == null) {
            return 0;
        }

        return crateOpenings.getOrDefault(
                crateId.toLowerCase(Locale.ROOT),
                0L
        );
    }

    // =====================================================
    // HENT ÅBNINGER FOR EN SPILLER
    // =====================================================

    public long getPlayerOpenings(UUID playerId) {

        if (playerId == null) {
            return 0;
        }

        return playerOpenings.getOrDefault(
                playerId,
                0L
        );
    }

    // =====================================================
    // HENT OVERSIGT OVER CRATE-TYPER
    // =====================================================

    public Map<String, Long> getCrateStatistics() {

        return Collections.unmodifiableMap(
                new HashMap<>(crateOpenings)
        );
    }

    // =====================================================
    // INDLÆS STATISTIK
    // =====================================================

    public void load() {

        loadedSuccessfully = false;

        if (!plugin.getDataFolder().exists()
                && !plugin.getDataFolder().mkdirs()) {

            plugin.getLogger().severe(
                    "Kunne ikke oprette mappen til crate-statistik!"
            );

            return;
        }

        YamlConfiguration config =
                new YamlConfiguration();

        // Ingen statistikfil endnu.
        // Vi starter derfor med tom statistik.
        if (!statisticsFile.exists()) {

            totalOpenings = 0;

            crateOpenings.clear();
            playerOpenings.clear();

            loadedSuccessfully = true;

            return;
        }

        try {

            config.load(statisticsFile);

        } catch (IOException
                 | InvalidConfigurationException exception) {

            plugin.getLogger().severe(
                    "Kunne ikke indlæse crate-stats.yml: "
                            + exception.getMessage()
            );

            // Vi overskriver ikke en fil,
            // som vi ikke kunne indlæse.
            return;
        }

        // =================================================
        // INDLÆS SAMLET ANTAL
        // =================================================

        long loadedTotal = Math.max(
                0,
                config.getLong("total-openings", 0)
        );

        // =================================================
        // INDLÆS CRATE-TYPER
        // =================================================

        Map<String, Long> loadedCrates =
                new HashMap<>();

        ConfigurationSection cratesSection =
                config.getConfigurationSection("crates");

        if (cratesSection != null) {

            for (String crateId : cratesSection.getKeys(false)) {

                long count = Math.max(
                        0,
                        cratesSection.getLong(crateId, 0)
                );

                loadedCrates.put(
                        crateId.toLowerCase(Locale.ROOT),
                        count
                );
            }
        }

        // =================================================
        // INDLÆS SPILLERE
        // =================================================

        Map<UUID, Long> loadedPlayers =
                new HashMap<>();

        ConfigurationSection playersSection =
                config.getConfigurationSection("players");

        if (playersSection != null) {

            for (String uuidText : playersSection.getKeys(false)) {

                try {

                    UUID playerId =
                            UUID.fromString(uuidText);

                    long count = Math.max(
                            0,
                            playersSection.getLong(uuidText, 0)
                    );

                    loadedPlayers.put(
                            playerId,
                            count
                    );

                } catch (IllegalArgumentException exception) {

                    plugin.getLogger().warning(
                            "Ugyldigt spiller-UUID i statistikken: "
                                    + uuidText
                    );
                }
            }
        }

        // =================================================
        // OPDATÉR STATISTIKKEN
        // =================================================

        totalOpenings = loadedTotal;

        crateOpenings.clear();
        crateOpenings.putAll(loadedCrates);

        playerOpenings.clear();
        playerOpenings.putAll(loadedPlayers);

        loadedSuccessfully = true;

        plugin.getLogger().info(
                "Crate-statistik indlæst. "
                        + "Samlede åbninger: "
                        + totalOpenings
        );
    }

    // =====================================================
    // GEM STATISTIK
    // =====================================================

    public boolean save() {

        if (!loadedSuccessfully) {

            plugin.getLogger().warning(
                    "Statistikken blev ikke gemt, "
                            + "fordi indlæsningen fejlede."
            );

            return false;
        }

        YamlConfiguration config =
                new YamlConfiguration();

        // =================================================
        // GEM SAMLET ANTAL
        // =================================================

        config.set(
                "total-openings",
                totalOpenings
        );

        // =================================================
        // GEM CRATE-TYPER
        // =================================================

        for (Map.Entry<String, Long> entry
                : crateOpenings.entrySet()) {

            config.set(
                    "crates." + entry.getKey(),
                    entry.getValue()
            );
        }

        // =================================================
        // GEM SPILLERE
        // =================================================

        for (Map.Entry<UUID, Long> entry
                : playerOpenings.entrySet()) {

            config.set(
                    "players." + entry.getKey(),
                    entry.getValue()
            );
        }

        // =================================================
        // SKRIV TIL FIL
        // =================================================

        try {

            config.save(statisticsFile);

            return true;

        } catch (IOException exception) {

            plugin.getLogger().severe(
                    "Kunne ikke gemme crate-statistik: "
                            + exception.getMessage()
            );

            return false;
        }
    }

    // =====================================================
    // SIKKER OPTÆLLING
    // =====================================================

    private long increment(long value) {

        if (value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        return value + 1;
    }
}
