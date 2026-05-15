package com.example.enchantpurge.core;

import com.example.enchantpurge.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link CostCalculator}.
 * <p>
 * Tests individual/bulk cost calculation, curse multipliers,
 * bypass permission, and item cost parsing.
 * Uses Mockito to stub {@link ConfigManager} — no Bukkit runtime required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CostCalculator")
class CostCalculatorTest {

    @Mock
    private ConfigManager configManager;

    private CostCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CostCalculator(configManager);

        // Default config values (matching spec defaults)
        lenient().doReturn(3).when(configManager).getIndividualExpLevel();
        lenient().doReturn(100.0).when(configManager).getIndividualMoney();
        lenient().doReturn(List.of()).when(configManager).getIndividualItemCosts();
        lenient().doReturn(10).when(configManager).getBulkExpLevel();
        lenient().doReturn(500.0).when(configManager).getBulkMoney();
        lenient().doReturn(List.of()).when(configManager).getBulkItemCosts();
        lenient().doReturn(2.0).when(configManager).getCursesExtraCostMultiplier();
    }

    // ── Helper for item costs stubbing ────────────────────────

    @SuppressWarnings("unchecked")
    private void stubItemCosts(List<?> rawItems) {
        lenient().doReturn(rawItems).when(configManager).getIndividualItemCosts();
    }

    @SuppressWarnings("unchecked")
    private void stubBulkItemCosts(List<?> rawItems) {
        lenient().doReturn(rawItems).when(configManager).getBulkItemCosts();
    }

    // ── Individual cost ───────────────────────────────────────

    @Nested
    @DisplayName("calculateIndividualCost")
    class IndividualCost {

        @Test
        @DisplayName("returns FREE when bypass is true")
        void bypassReturnsFree() {
            CostCalculator.CostResult result = calculator.calculateIndividualCost(true, false);
            assertTrue(result.isFree());
            assertEquals(0, result.expLevels());
            assertEquals(0.0, result.money());
            assertTrue(result.itemCosts().isEmpty());
        }

        @Test
        @DisplayName("returns normal exp cost from config")
        void normalExpCost() {
            lenient().doReturn(5).when(configManager).getIndividualExpLevel();
            lenient().doReturn(0.0).when(configManager).getIndividualMoney();

            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, false);

            assertEquals(5, result.expLevels());
            assertEquals(0.0, result.money());
        }

        @Test
        @DisplayName("returns money cost from config")
        void normalMoneyCost() {
            lenient().doReturn(250.0).when(configManager).getIndividualMoney();

            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, false);

            assertEquals(3, result.expLevels());
            assertEquals(250.0, result.money());
        }

        @Test
        @DisplayName("parses item costs from config (Map format)")
        void itemCostsFromConfig() {
            stubItemCosts(List.of(Map.of("material", "DIAMOND", "amount", 3)));
            lenient().doReturn(0.0).when(configManager).getIndividualMoney();

            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, false);

            assertEquals(1, result.itemCosts().size());
            assertEquals("DIAMOND", result.itemCosts().get(0).material());
            assertEquals(3, result.itemCosts().get(0).amount());
        }

        @Test
        @DisplayName("handles empty item costs list")
        void emptyItemCosts() {
            stubItemCosts(List.of());

            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, false);

            assertTrue(result.itemCosts().isEmpty());
        }

        @Test
        @DisplayName("handles null item costs list")
        void nullItemCosts() {
            stubItemCosts(null);

            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, false);

            assertTrue(result.itemCosts().isEmpty());
        }
    }

    // ── Bulk cost ─────────────────────────────────────────────

    @Nested
    @DisplayName("calculateBulkCost")
    class BulkCost {

        @Test
        @DisplayName("returns FREE when bypass is true")
        void bypassReturnsFree() {
            CostCalculator.CostResult result = calculator.calculateBulkCost(true, 0);
            assertTrue(result.isFree());
        }

        @Test
        @DisplayName("returns bulk exp cost from config")
        void bulkExpCost() {
            lenient().doReturn(15).when(configManager).getBulkExpLevel();
            lenient().doReturn(0.0).when(configManager).getBulkMoney();

            CostCalculator.CostResult result = calculator.calculateBulkCost(false, 0);

            assertEquals(15, result.expLevels());
        }

        @Test
        @DisplayName("returns bulk money cost from config")
        void bulkMoneyCost() {
            lenient().doReturn(1000.0).when(configManager).getBulkMoney();

            CostCalculator.CostResult result = calculator.calculateBulkCost(false, 0);

            assertEquals(10, result.expLevels());
            assertEquals(1000.0, result.money());
        }

        @Test
        @DisplayName("bulk item costs from config")
        void bulkItemCosts() {
            stubBulkItemCosts(List.of(Map.of("material", "LAPIS_LAZULI", "amount", 5)));
            lenient().doReturn(0.0).when(configManager).getBulkMoney();

            CostCalculator.CostResult result = calculator.calculateBulkCost(false, 0);

            assertEquals(1, result.itemCosts().size());
            assertEquals("LAPIS_LAZULI", result.itemCosts().get(0).material());
            assertEquals(5, result.itemCosts().get(0).amount());
        }
    }

    // ── Curse multiplier ──────────────────────────────────────

    @Nested
    @DisplayName("curse multiplier")
    class CurseMultiplier {

        @Test
        @DisplayName("individual cost applies curse multiplier when isCurse=true")
        void individualCurseAppliesMultiplier() {
            lenient().doReturn(3.0).when(configManager).getCursesExtraCostMultiplier();

            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, true);

            // 3 * 3.0 = 9 exp levels
            assertEquals(9, result.expLevels());
            // 100.0 * 3.0 = 300.0 money
            assertEquals(300.0, result.money(), 0.001);
        }

        @Test
        @DisplayName("individual cost does NOT apply multiplier when isCurse=false")
        void individualNoCurseNoMultiplier() {
            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, false);

            assertEquals(3, result.expLevels());
            assertEquals(100.0, result.money(), 0.001);
        }

        @Test
        @DisplayName("bulk cost applies curse multiplier when curseCount > 0")
        void bulkCurseAppliesMultiplier() {
            lenient().doReturn(2.0).when(configManager).getCursesExtraCostMultiplier();
            lenient().doReturn(10).when(configManager).getBulkExpLevel();
            lenient().doReturn(500.0).when(configManager).getBulkMoney();

            CostCalculator.CostResult result = calculator.calculateBulkCost(false, 2);

            assertEquals(20, result.expLevels());
            assertEquals(1000.0, result.money(), 0.001);
        }

        @Test
        @DisplayName("bulk cost does NOT apply multiplier when curseCount == 0")
        void bulkNoCurseNoMultiplier() {
            CostCalculator.CostResult result = calculator.calculateBulkCost(false, 0);

            assertEquals(10, result.expLevels());
            assertEquals(500.0, result.money(), 0.001);
        }

        @Test
        @DisplayName("bulk cost with curseCount=0 uses multiplier=1.0")
        void bulkZeroCurseCount() {
            lenient().doReturn(5.0).when(configManager).getCursesExtraCostMultiplier();
            lenient().doReturn(10).when(configManager).getBulkExpLevel();

            CostCalculator.CostResult result = calculator.calculateBulkCost(false, 0);

            // curseCount==0 → multiplier=1.0, NOT 5.0
            assertEquals(10, result.expLevels());
        }
    }

    // ── Item cost multiplier for curses ───────────────────────

    @Nested
    @DisplayName("item cost with curse multiplier")
    class ItemCostCurseMultiplier {

        @Test
        @DisplayName("item amount is multiplied by curse multiplier and floored at 1")
        void itemAmountMultipliedForCurse() {
            lenient().doReturn(2.0).when(configManager).getCursesExtraCostMultiplier();
            lenient().doReturn(0.0).when(configManager).getIndividualMoney();
            stubItemCosts(List.of(Map.of("material", "DIAMOND", "amount", 3)));

            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, true);

            assertEquals(1, result.itemCosts().size());
            // 3 * 2.0 = 6
            assertEquals(6, result.itemCosts().get(0).amount());
        }

        @Test
        @DisplayName("item amount minimum is 1 even after rounding")
        void itemAmountMinimumOne() {
            lenient().doReturn(0.1).when(configManager).getCursesExtraCostMultiplier();
            lenient().doReturn(0.0).when(configManager).getIndividualMoney();
            stubItemCosts(List.of(Map.of("material", "DIAMOND", "amount", 3)));

            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, true);

            // 3 * 0.1 = 0.3, Math.round = 0, Math.max(1, 0) = 1
            assertEquals(1, result.itemCosts().get(0).amount());
        }
    }

    // ── CostResult record ─────────────────────────────────────

    @Nested
    @DisplayName("CostResult record")
    class CostResultTests {

        @Test
        @DisplayName("FREE is zero cost")
        void freeIsZero() {
            assertTrue(CostCalculator.CostResult.FREE.isFree());
            assertEquals(0, CostCalculator.CostResult.FREE.expLevels());
            assertEquals(0.0, CostCalculator.CostResult.FREE.money());
            assertTrue(CostCalculator.CostResult.FREE.itemCosts().isEmpty());
        }

        @Test
        @DisplayName("isFree returns true only when all costs are zero")
        void isFreeLogic() {
            assertTrue(new CostCalculator.CostResult(0, 0.0, List.of()).isFree());
            assertFalse(new CostCalculator.CostResult(1, 0.0, List.of()).isFree());
            assertFalse(new CostCalculator.CostResult(0, 1.0, List.of()).isFree());
            assertFalse(new CostCalculator.CostResult(0, 0.0,
                    List.of(new CostCalculator.ItemCostEntry("DIAMOND", 1))).isFree());
        }

        @Test
        @DisplayName("toString produces readable output")
        void toStringReadable() {
            CostCalculator.CostResult result = new CostCalculator.CostResult(5, 100.0, List.of());
            String str = result.toString();
            assertTrue(str.contains("exp:5"));
            assertTrue(str.contains("money:100.0"));
        }
    }

    // ── ItemCostEntry record ──────────────────────────────────

    @Nested
    @DisplayName("ItemCostEntry record")
    class ItemCostEntryTests {

        @Test
        @DisplayName("toString produces readable output")
        void toStringReadable() {
            CostCalculator.ItemCostEntry entry = new CostCalculator.ItemCostEntry("DIAMOND", 3);
            assertEquals("DIAMOND x3", entry.toString());
        }
    }

    // ── Vault disabled (money = 0) ────────────────────────────

    @Nested
    @DisplayName("Vault disabled handling")
    class VaultDisabled {

        @Test
        @DisplayName("money cost from config is used as-is (MoneyCost handles Vault availability)")
        void moneyCostFromConfigPassedThrough() {
            // CostCalculator just reads and passes through the config value.
            // MoneyCost itself checks Vault availability and treats money==0
            // or economy==null as satisfied.
            // This test verifies CostCalculator doesn't zero-out money on its own.
            lenient().doReturn(100.0).when(configManager).getIndividualMoney();

            CostCalculator.CostResult result = calculator.calculateIndividualCost(false, false);

            // CostCalculator faithfully returns whatever config says
            assertEquals(100.0, result.money(), 0.001);
        }
    }
}
