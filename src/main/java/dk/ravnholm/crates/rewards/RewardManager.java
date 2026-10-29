
package dk.ravnholm.crates.rewards;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RewardManager {

    // =====================================================
    // BELØNNINGER OPDELT EFTER CRATE-ID
    // =====================================================

    private final Map<String, List<Reward>> rewards =
            new HashMap<>();

    // =====================================================
    // NORMALISER CRATE-ID
    // =====================================================

    private String normalizeCrateId(String crateId) {

        if (crateId == null || crateId.isBlank()) {
            return null;
        }

        return crateId.trim().toLowerCase(Locale.ROOT);
    }

    // =====================================================
    // REGISTRER EN BELØNNING
    // =====================================================

    public void registerReward(
            String crateId,
            Reward reward
    ) {

        String normalizedId = normalizeCrateId(crateId);

        if (normalizedId == null || reward == null) {

            throw new IllegalArgumentException(
                    "Ugyldig crate eller belønning."
            );
        }

        // =================================================
        // BRUG PRÆCISE VÆGTENHEDER
        // =================================================

        // Reward.java omregner alle vægte til
        // heltal med seks decimalers præcision.
        //
        // Eksempler:
        //
        // 50       = 50_000_000
        // 0.5      =    500_000
        // 0.132132 =    132_132
        // 0.000001 =          1
        //
        // Vi må IKKE bruge getWeight(), da
        // den afrunder decimalvægte opad.

        long weight = reward.getWeightUnits();

        if (weight <= 0) {

            Bukkit.getLogger().warning(
                    "[RavnholmCrates] Belønningen '"
                            + reward.getId()
                            + "' i crate '"
                            + normalizedId
                            + "' har ugyldig vægt: "
                            + reward.getExactWeight()
                            + ". Belønningen springes over."
            );

            return;
        }

        // =================================================
        // FIND EKSISTERENDE BELØNNINGER
        // =================================================

        List<Reward> crateRewards =
                rewards.computeIfAbsent(
                        normalizedId,
                        key -> new ArrayList<>()
                );

        // =================================================
        // KONTROLLÉR SAMLET VÆGT
        // =================================================

        long existingWeight = 0;

        for (Reward existingReward : crateRewards) {

            long existing =
                    existingReward.getWeightUnits();

            if (existing <= 0) {

                Bukkit.getLogger().warning(
                        "[RavnholmCrates] Ugyldig vægt "
                                + "fundet i crate '"
                                + normalizedId + "'."
                );

                return;
            }

            // Forhindr long-overflow.

            if (existing > Long.MAX_VALUE
                    - existingWeight) {

                Bukkit.getLogger().warning(
                        "[RavnholmCrates] Vægtgrænsen "
                                + "er overskredet for crate '"
                                + normalizedId + "'."
                );

                return;
            }

            existingWeight += existing;
        }

        // =================================================
        // KONTROLLÉR NY BELØNNING
        // =================================================

        if (weight > Long.MAX_VALUE
                - existingWeight) {

            Bukkit.getLogger().warning(
                    "[RavnholmCrates] Belønningen '"
                            + reward.getId()
                            + "' kan ikke registreres. "
                            + "Den samlede vægt for crate '"
                            + normalizedId
                            + "' ville blive for stor."
            );

            return;
        }

        // =================================================
        // REGISTRER BELØNNINGEN
        // =================================================

        crateRewards.add(reward);
    }

    // =====================================================
    // HENT ALLE BELØNNINGER TIL EN CRATE
    // =====================================================

    public List<Reward> getRewards(String crateId) {

        String normalizedId = normalizeCrateId(crateId);

        if (normalizedId == null) {
            return List.of();
        }

        List<Reward> crateRewards =
                rewards.get(normalizedId);

        if (crateRewards == null) {
            return List.of();
        }

        // Returner en kopi, så andre klasser
        // ikke kan ændre listen direkte.

        return List.copyOf(crateRewards);
    }

    // =====================================================
    // VÆLG TILFÆLDIG BELØNNING UD FRA VÆGT
    // =====================================================

    public Reward rollReward(String crateId) {

        String normalizedId = normalizeCrateId(crateId);

        if (normalizedId == null) {
            return null;
        }

        List<Reward> crateRewards =
                getRewards(normalizedId);

        if (crateRewards.isEmpty()) {
            return null;
        }

        // =================================================
        // BEREGN SAMLET VÆGT
        // =================================================

        long totalWeight = 0;

        for (Reward reward : crateRewards) {

            // VIGTIGT:
            // Brug præcise vægtenheder.

            long weight = reward.getWeightUnits();

            if (weight <= 0) {

                Bukkit.getLogger().warning(
                        "[RavnholmCrates] Udtrækning afvist "
                                + "for crate '"
                                + normalizedId
                                + "': ugyldig belønningsvægt."
                );

                return null;
            }

            // Forhindr long-overflow.

            if (weight > Long.MAX_VALUE
                    - totalWeight) {

                Bukkit.getLogger().warning(
                        "[RavnholmCrates] Udtrækning afvist "
                                + "for crate '"
                                + normalizedId
                                + "': samlet vægt er for stor."
                );

                return null;
            }

            totalWeight += weight;
        }

        // =================================================
        // KONTROLLÉR SAMLET VÆGT
        // =================================================

        if (totalWeight <= 0) {
            return null;
        }

        // =================================================
        // TRÆK ET TILFÆLDIGT TAL
        // =================================================

        // nextLong(totalWeight) vælger et tal
        // fra 0 (inklusive) til totalWeight
        // (eksklusive).
        //
        // Det betyder, at alle vægtenheder
        // har samme sandsynlighed.

        long random = ThreadLocalRandom.current()
                .nextLong(totalWeight);

        // =================================================
        // FIND DEN VINDENDE BELØNNING
        // =================================================

        long currentWeight = 0;

        for (Reward reward : crateRewards) {

            currentWeight += reward.getWeightUnits();

            if (random < currentWeight) {
                return reward;
            }
        }

        // =================================================
        // SIKKERHED VED UVENTET FEJL
        // =================================================

        Bukkit.getLogger().warning(
                "[RavnholmCrates] Ingen belønning blev "
                        + "fundet ved udtrækning for crate '"
                        + normalizedId + "'."
        );

        return null;
    }
}
