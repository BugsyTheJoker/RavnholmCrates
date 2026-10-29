
package dk.ravnholm.crates.holograms;

import dk.ravnholm.crates.RavnholmCrates;
import dk.ravnholm.crates.crates.Crate;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CrateHologramManager {

    // =====================================================
    // INDSTILLINGER
    // =====================================================

    private static final String HOLOGRAM_PREFIX =
            "ravnholmcrates_";

    private static final double DEFAULT_HEIGHT = 1.5;

    // =====================================================
    // FELTER
    // =====================================================

    private final RavnholmCrates plugin;

    private final Set<String> managedHolograms =
            new HashSet<>();

    private boolean enabled;

    // =====================================================
    // KONSTRUKTØR
    // =====================================================

    public CrateHologramManager(RavnholmCrates plugin) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                    "Plugin må ikke være null."
            );
        }

        this.plugin = plugin;

        this.enabled = plugin.getServer()
                .getPluginManager()
                .isPluginEnabled("FancyHolograms");

        if (enabled) {

            plugin.getLogger().info(
                    "FancyHolograms-integration aktiveret."
            );

        } else {

            plugin.getLogger().warning(
                    "FancyHolograms blev ikke fundet. "
                            + "Crates fungerer uden hologrammer."
            );
        }
    }

    // =====================================================
    // STATUS
    // =====================================================

    public boolean isEnabled() {
        return enabled;
    }

    // =====================================================
    // HENT FANCYHOLOGRAMS MANAGER
    // =====================================================

    private HologramManager getManager() {

        if (!enabled) {
            return null;
        }

        if (!plugin.getServer()
                .getPluginManager()
                .isPluginEnabled("FancyHolograms")) {

            enabled = false;
            return null;
        }

        return FancyHologramsPlugin.get()
                .getHologramManager();
    }

    // =====================================================
    // GENERÉR UNIKT HOLOGRAM-ID
    // =====================================================

    private String getHologramId(Location location) {

        if (location == null
                || location.getWorld() == null) {

            return null;
        }

        String worldId = location.getWorld()
                .getUID()
                .toString()
                .replace("-", "");

        return HOLOGRAM_PREFIX
                + worldId
                + "_"
                + location.getBlockX()
                + "_"
                + location.getBlockY()
                + "_"
                + location.getBlockZ();
    }

    // =====================================================
    // OPRET ELLER OPDATER HOLOGRAM
    // =====================================================

    public void syncHologram(
            Location blockLocation,
            String crateId
    ) {

        if (!enabled
                || blockLocation == null
                || blockLocation.getWorld() == null
                || crateId == null) {

            return;
        }

        Crate crate = plugin.getCrateManager()
                .getCrate(crateId);

        if (crate == null) {

            // Ukendte crate-typer får ikke hologrammer.
            return;
        }

        String hologramId = getHologramId(
                blockLocation
        );

        if (hologramId == null) {
            return;
        }

        // =================================================
        // INDLÆS HOLOGRAMINDSTILLINGER
        // =================================================

        File file = new File(
                plugin.getDataFolder(),
                "crate-types.yml"
        );

        YamlConfiguration config =
                YamlConfiguration.loadConfiguration(file);

        String path = "crates."
                + crate.getId()
                + ".hologram";

        ConfigurationSection section =
                config.getConfigurationSection(path);

        boolean visible = section == null
                || section.getBoolean("enabled", true);

        if (!visible) {

            removeHologram(blockLocation);
            return;
        }

        double height = DEFAULT_HEIGHT;

        if (section != null) {

            height = section.getDouble(
                    "height",
                    DEFAULT_HEIGHT
            );
        }

        if (!Double.isFinite(height)
                || height < 0
                || height > 10) {

            plugin.getLogger().warning(
                    "Ugyldig hologramhøjde for crate '"
                            + crate.getId()
                            + "'. Bruger standardhøjde."
            );

            height = DEFAULT_HEIGHT;
        }

        List<String> lines = getDefaultLines(
                crate.getId()
        );

        if (section != null
                && section.isList("lines")) {

            List<String> configuredLines =
                    section.getStringList("lines");

            if (!configuredLines.isEmpty()) {
                lines = configuredLines;
            }
        }

        // =================================================
        // BEREGN PLACERING
        // =================================================

        Location hologramLocation =
                blockLocation.getBlock()
                        .getLocation()
                        .add(
                                0.5,
                                height,
                                0.5
                        );

        // =================================================
        // OPRET ELLER OPDATER VIA FANCYHOLOGRAMS
        // =================================================

        try {

            HologramManager manager = getManager();

            if (manager == null
                    || !manager.isLoaded()) {

                return;
            }

            Hologram existing = manager
                    .getHologram(hologramId)
                    .orElse(null);

            if (existing != null) {

                // Opdater et eksisterende hologram
                // frem for at oprette en dublet.

                if (!(existing.getData()
                        instanceof TextHologramData textData)) {

                    plugin.getLogger().warning(
                            "Hologram-ID er optaget af "
                                    + "et andet hologram: "
                                    + hologramId
                    );

                    return;
                }

                textData.setText(lines);

                textData.setBackground(
                        Color.fromARGB(0, 0, 0, 0)
                );

                textData.setTextShadow(false);

                textData.setTextAlignment(
                        TextDisplay.TextAlignment.CENTER
                );

                textData.setBillboard(
                        Display.Billboard.CENTER
                );

                textData.setPersistent(false);

                existing.queueUpdate();

                managedHolograms.add(hologramId);

                return;
            }

            // =================================================
            // OPRET NYT TEKSTHOLOGRAM
            // =================================================

            TextHologramData data =
                    new TextHologramData(
                            hologramId,
                            hologramLocation
                    );

            data.setText(lines);

            // Gennemsigtig baggrund.
            data.setBackground(
                    Color.fromARGB(0, 0, 0, 0)
            );

            // Ingen tekstskygge.
            data.setTextShadow(false);

            data.setTextAlignment(
                    TextDisplay.TextAlignment.CENTER
            );

            // Hologrammet vender mod spilleren.
            data.setBillboard(
                    Display.Billboard.CENTER
            );

            // CrateManager gemmer placeringerne.
            // FancyHolograms skal derfor ikke
            // gemme en ekstra permanent kopi.
            data.setPersistent(false);

            Hologram hologram =
                    manager.create(data);

            manager.addHologram(hologram);

            managedHolograms.add(hologramId);

        } catch (RuntimeException | LinkageError exception) {

            plugin.getLogger().warning(
                    "Kunne ikke oprette hologram for crate '"
                            + crate.getId()
                            + "': "
                            + exception.getMessage()
            );
        }
    }

    // =====================================================
    // FJERN HOLOGRAM
    // =====================================================

    public void removeHologram(Location location) {

        if (!enabled || location == null) {
            return;
        }

        String hologramId =
                getHologramId(location);

        if (hologramId == null) {
            return;
        }

        try {

            HologramManager manager = getManager();

            if (manager == null) {
                return;
            }

            manager.getHologram(hologramId)
                    .ifPresent(manager::removeHologram);

            managedHolograms.remove(hologramId);

        } catch (RuntimeException | LinkageError exception) {

            plugin.getLogger().warning(
                    "Kunne ikke fjerne crate-hologram: "
                            + exception.getMessage()
            );
        }
    }

    // =====================================================
    // SYNKRONISER ALLE CRATES
    // =====================================================

    // Modtager alle aktive placeringer fra CrateManager.
    //
    // Vi kobler denne metode til CrateManager
    // og RavnholmCrates i de næste trin.

    public void syncAll(
            Map<Location, String> crateLocations
    ) {

        if (!enabled || crateLocations == null) {
            return;
        }

        Set<String> expectedHolograms =
                new HashSet<>();

        for (Map.Entry<Location, String> entry
                : crateLocations.entrySet()) {

            Location location = entry.getKey();
            String crateId = entry.getValue();

            if (location == null
                    || location.getWorld() == null) {

                continue;
            }

            syncHologram(location, crateId);

            String hologramId =
                    getHologramId(location);

            if (hologramId != null
                    && managedHolograms.contains(
                    hologramId
            )) {

                expectedHolograms.add(hologramId);
            }
        }

        // =================================================
        // FJERN HOLOGRAMMER FRA SLETTEDE CRATES
        // =================================================

        Set<String> obsoleteHolograms =
                new HashSet<>(managedHolograms);

        obsoleteHolograms.removeAll(
                expectedHolograms
        );

        for (String hologramId : obsoleteHolograms) {

            try {

                HologramManager manager = getManager();

                if (manager == null) {
                    return;
                }

                manager.getHologram(hologramId)
                        .ifPresent(manager::removeHologram);

                managedHolograms.remove(hologramId);

            } catch (RuntimeException | LinkageError exception) {

                plugin.getLogger().warning(
                        "Kunne ikke rydde op i hologram '"
                                + hologramId
                                + "': "
                                + exception.getMessage()
                );
            }
        }
    }

    // =====================================================
    // STANDARDTEKSTER
    // =====================================================

    private List<String> getDefaultLines(
            String crateId
    ) {

        String id = crateId
                .toLowerCase(Locale.ROOT);

        if (id.equals("vote")
                || id.equals("votecrate")) {

            return List.of(
                    "<green><bold>✦ VOTE CRATE ✦</bold></green>",
                    "<gray>Stem på Ravnholm SMP</gray>",
                    "<yellow>Brug en Vote Key</yellow>",
                    "<white>Højreklik for at åbne</white>"
            );
        }

        if (id.equals("royal")
                || id.equals("royalcrate")) {

            return List.of(
                    "<gradient:#FFB347:#FF4545><bold>"
                            + "✦ ROYAL CRATE ✦"
                            + "</bold></gradient>",
                    "<gray>Eksklusiv event-crate</gray>",
                    "<gold>Brug en Royal Key</gold>",
                    "<white>Højreklik for at åbne</white>"
            );
        }

        if (id.equals("ravnholm")
                || id.equals("ravnholmcrate")) {

            return List.of(
                    "<gradient:#FFD700:#AA55FF><bold>"
                            + "✦ RAVNHOLM CRATE ✦"
                            + "</bold></gradient>",
                    "<gray>Ravnholms mest sjældne skatte</gray>",
                    "<light_purple>Brug en Ravnholm Key"
                            + "</light_purple>",
                    "<white>Højreklik for at åbne</white>"
            );
        }

        return List.of(
                "<gold><bold>✦ CRATE ✦</bold></gold>",
                "<gray>En skat venter på dig</gray>",
                "<yellow>Brug den tilhørende nøgle</yellow>",
                "<white>Højreklik for at åbne</white>"
        );
    }

    // =====================================================
    // STOP INTEGRATIONEN
    // =====================================================

    public void shutdown() {

        if (!enabled) {
            managedHolograms.clear();
            return;
        }

        try {

            HologramManager manager = getManager();

            if (manager != null) {

                for (String hologramId
                        : new HashSet<>(managedHolograms)) {

                    manager.getHologram(hologramId)
                            .ifPresent(manager::removeHologram);
                }
            }

        } catch (RuntimeException | LinkageError exception) {

            plugin.getLogger().warning(
                    "Fejl under lukning af hologramintegration: "
                            + exception.getMessage()
            );

        } finally {

            managedHolograms.clear();
            enabled = false;
        }
    }
}
