
package dk.ravnholm.crates.rewards;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Locale;

public class Reward {

    // =====================================================
    // BELØNNINGSTYPER
    // =====================================================

    public enum RewardType {
        ITEM,
        MONEY,
        KEY
    }

    // Seks decimalers præcision.
    // Eksempel:
    // 0.132132 bliver til 132132 vægtenheder.
    private static final long WEIGHT_SCALE = 1_000_000L;

    // =====================================================
    // FELTER
    // =====================================================

    private final String id;
    private final String name;
    private final ItemStack item;

    private final RewardType type;

    private final double weight;
    private final long weightUnits;

    private final double moneyAmount;

    private final String keyCrateId;
    private final int keyAmount;

    // =====================================================
    // EKSISTERENDE CONSTRUCTOR
    // =====================================================

    // Bevares for kompatibilitet med den nuværende kode.

    public Reward(
            String id,
            String name,
            ItemStack item,
            int weight
    ) {
        this(
                id,
                name,
                item,
                RewardType.ITEM,
                weight,
                0,
                null,
                0
        );
    }

    // =====================================================
    // INTERN CONSTRUCTOR
    // =====================================================

    private Reward(
            String id,
            String name,
            ItemStack item,
            RewardType type,
            double weight,
            double moneyAmount,
            String keyCrateId,
            int keyAmount
    ) {

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Reward ID må ikke være tom."
            );
        }

        if (item == null || item.getType().isAir()) {
            throw new IllegalArgumentException(
                    "Reward skal have en gyldig genstand."
            );
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "Reward-type må ikke være null."
            );
        }

        if (!Double.isFinite(weight) || weight <= 0) {
            throw new IllegalArgumentException(
                    "Reward-vægt skal være et positivt tal."
            );
        }

        long calculatedUnits;

        try {
            calculatedUnits = BigDecimal.valueOf(weight)
                    .multiply(BigDecimal.valueOf(WEIGHT_SCALE))
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Reward-vægt skal have højst seks decimaler "
                            + "og være inden for den tilladte grænse.",
                    exception
            );
        }

        if (calculatedUnits <= 0) {
            throw new IllegalArgumentException(
                    "Reward-vægt er for lille."
            );
        }

        if (type == RewardType.MONEY) {

            if (!Double.isFinite(moneyAmount)
                    || moneyAmount <= 0) {

                throw new IllegalArgumentException(
                        "Pengepræmien skal være større end 0."
                );
            }
        }

        if (type == RewardType.KEY) {

            if (keyCrateId == null
                    || keyCrateId.isBlank()) {

                throw new IllegalArgumentException(
                        "En nøglepræmie skal have et crate-ID."
                );
            }

            if (keyAmount < 1 || keyAmount > 64) {
                throw new IllegalArgumentException(
                        "Antallet af nøgler skal være mellem 1 og 64."
                );
            }
        }

        this.id = id.trim();
        this.name = name == null || name.isBlank()
                ? this.id
                : name;

        this.item = item.clone();
        this.type = type;

        this.weight = weight;
        this.weightUnits = calculatedUnits;

        this.moneyAmount = moneyAmount;

        this.keyCrateId = keyCrateId == null
                ? null
                : keyCrateId.trim().toLowerCase(Locale.ROOT);

        this.keyAmount = keyAmount;
    }

    // =====================================================
    // OPRET ALMINDELIG ITEM-BELØNNING
    // =====================================================

    public static Reward item(
            String id,
            String name,
            ItemStack item,
            double weight
    ) {

        return new Reward(
                id,
                name,
                item,
                RewardType.ITEM,
                weight,
                0,
                null,
                0
        );
    }

    // =====================================================
    // OPRET PENGEBELØNNING
    // =====================================================

    // DisplayItem bruges kun til at vise præmien
    // i GUI'en. Spilleren skal ikke modtage itemet.
    //
    // Selve udbetalingen implementeres senere
    // i vores reward-udlevering.

    public static Reward money(
            String id,
            String name,
            ItemStack displayItem,
            double amount,
            double weight
    ) {

        return new Reward(
                id,
                name,
                displayItem,
                RewardType.MONEY,
                weight,
                amount,
                null,
                0
        );
    }

    // =====================================================
    // OPRET NØGLEBELØNNING
    // =====================================================

    // DisplayItem bruges i GUI'en.
    //
    // Den rigtige nøgle skal senere oprettes
    // gennem KeyManager.createKey().
    //
    // Dermed får nøglen den korrekte PDC-data.

    public static Reward key(
            String id,
            String name,
            ItemStack displayItem,
            String targetCrateId,
            int amount,
            double weight
    ) {

        return new Reward(
                id,
                name,
                displayItem,
                RewardType.KEY,
                weight,
                0,
                targetCrateId,
                amount
        );
    }

    // =====================================================
    // GRUNDLÆGGENDE GETTERS
    // =====================================================

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    // Returnerer altid en kopi.
    //
    // For MONEY og KEY er dette visningsgenstanden,
    // ikke den præmie der skal udleveres.

    public ItemStack getItem() {
        return item.clone();
    }

    public RewardType getType() {
        return type;
    }

    // =====================================================
    // VÆGTE
    // =====================================================

    // Eksisterende metode bevares, så den nuværende
    // kode fortsat kan kompilere.
    //
    // For decimalvægte er resultatet afrundet opad.
    // Denne metode må derfor IKKE bruges til den
    // endelige lodtrækning af nye crates.

    @Deprecated
    public int getWeight() {

        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.ceil(weight)
        );
    }

    // Den oprindelige vægt, eksempelvis 0.132132.

    public double getExactWeight() {
        return weight;
    }

    // Brug denne til lodtrækningen.
    //
    // Eksempel:
    // 25.0     -> 25000000
    // 0.724    -> 724000
    // 0.132132 -> 132132

    public long getWeightUnits() {
        return weightUnits;
    }

    // =====================================================
    // PENGEBELØNNINGER
    // =====================================================

    public boolean isMoneyReward() {
        return type == RewardType.MONEY;
    }

    public double getMoneyAmount() {
        return moneyAmount;
    }

    // =====================================================
    // NØGLEBELØNNINGER
    // =====================================================

    public boolean isKeyReward() {
        return type == RewardType.KEY;
    }

    public String getKeyCrateId() {
        return keyCrateId;
    }

    public int getKeyAmount() {
        return keyAmount;
    }

    // =====================================================
    // ITEM-BELØNNINGER
    // =====================================================

    public boolean isItemReward() {
        return type == RewardType.ITEM;
    }
}
