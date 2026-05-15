package com.example.enchantpurge.cost;

import com.example.enchantpurge.core.CostCalculator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ItemCost}.
 * <p>
 * Tests {@code hasItems()} and {@code consumeItems()} with mock
 * {@link Player} and {@link PlayerInventory}, verifying material
 * resolution, edge cases, and consumption behavior.
 * <p>
 * <b>Limitation:</b> {@code consumeItems()} mutates {@link ItemStack} amounts
 * in-place. Mockito mocks do not track mutable state natively, so dynamic
 * per-slot amount changes are verified via mock interactions rather than
 * exact post-consumption counts. Full inventory mutation testing requires
 * integration tests with a real server or MockBukkit.
 */
@DisplayName("ItemCost")
class ItemCostTest {

    private ItemCost itemCost;

    @BeforeEach
    void setUp() {
        itemCost = new ItemCost();
    }

    // ── hasItems edge cases ──────────────────────────────────

    @Nested
    @DisplayName("hasItems() edge cases")
    class HasItemsEdgeCases {

        @Test
        @DisplayName("null list returns true")
        void nullListReturnsTrue() {
            assertTrue(itemCost.hasItems(null, null));
        }

        @Test
        @DisplayName("empty list returns true")
        void emptyListReturnsTrue() {
            assertTrue(itemCost.hasItems(null, List.of()));
        }

        @Test
        @DisplayName("invalid material name returns false")
        void invalidMaterialNameReturnsFalse() {
            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("INVALID_MATERIAL_XYZ", 1));
            assertFalse(itemCost.hasItems(null, costs));
        }
    }

    // ── hasItems with Player mock ────────────────────────────

    @Nested
    @DisplayName("hasItems() with Player inventory")
    class HasItemsWithPlayer {

        private Player player;
        private PlayerInventory inventory;

        @BeforeEach
        void setUp() {
            player = mock(Player.class);
            inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
        }

        @Test
        @DisplayName("returns true when player has sufficient items")
        void sufficientItemsReturnsTrue() {
            ItemStack diamond1 = createItemStackStub(Material.DIAMOND, 5);
            when(inventory.getContents()).thenReturn(new ItemStack[]{diamond1});

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("DIAMOND", 3));
            assertTrue(itemCost.hasItems(player, costs));
        }

        @Test
        @DisplayName("returns false when player has insufficient quantity")
        void insufficientQuantityReturnsFalse() {
            ItemStack diamond1 = createItemStackStub(Material.DIAMOND, 2);
            when(inventory.getContents()).thenReturn(new ItemStack[]{diamond1});

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("DIAMOND", 5));
            assertFalse(itemCost.hasItems(player, costs));
        }

        @Test
        @DisplayName("aggregates items across multiple slots")
        void aggregatesAcrossSlots() {
            ItemStack stack1 = createItemStackStub(Material.DIAMOND, 2);
            ItemStack stack2 = createItemStackStub(Material.DIAMOND, 3);
            when(inventory.getContents()).thenReturn(new ItemStack[]{stack1, stack2});

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("DIAMOND", 4));
            assertTrue(itemCost.hasItems(player, costs));
        }

        @Test
        @DisplayName("returns false when material not in inventory")
        void materialNotInInventoryReturnsFalse() {
            ItemStack ironStack = createItemStackStub(Material.IRON_INGOT, 10);
            when(inventory.getContents()).thenReturn(new ItemStack[]{ironStack});

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("DIAMOND", 1));
            assertFalse(itemCost.hasItems(player, costs));
        }

        @Test
        @DisplayName("skips null slots in inventory")
        void skipsNullSlots() {
            ItemStack diamondStack = createItemStackStub(Material.DIAMOND, 3);
            when(inventory.getContents()).thenReturn(
                    new ItemStack[]{null, diamondStack, null});

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("DIAMOND", 3));
            assertTrue(itemCost.hasItems(player, costs));
        }

        @Test
        @DisplayName("requires all item types to be present")
        void multipleItemTypesRequired() {
            ItemStack diamondStack = createItemStackStub(Material.DIAMOND, 5);
            ItemStack ironStack = createItemStackStub(Material.IRON_INGOT, 3);
            when(inventory.getContents()).thenReturn(
                    new ItemStack[]{diamondStack, ironStack});

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("DIAMOND", 2),
                    new CostCalculator.ItemCostEntry("IRON_INGOT", 3));
            assertTrue(itemCost.hasItems(player, costs));
        }

        @Test
        @DisplayName("returns false when one of multiple types is missing")
        void oneTypeMissingReturnsFalse() {
            ItemStack diamondStack = createItemStackStub(Material.DIAMOND, 5);
            when(inventory.getContents()).thenReturn(new ItemStack[]{diamondStack});

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("DIAMOND", 2),
                    new CostCalculator.ItemCostEntry("IRON_INGOT", 3));
            assertFalse(itemCost.hasItems(player, costs));
        }
    }

    // ── consumeItems edge cases ──────────────────────────────

    @Nested
    @DisplayName("consumeItems() edge cases")
    class ConsumeItemsEdgeCases {

        @Test
        @DisplayName("null list returns true")
        void nullListReturnsTrue() {
            assertTrue(itemCost.consumeItems(null, null));
        }

        @Test
        @DisplayName("empty list returns true")
        void emptyListReturnsTrue() {
            assertTrue(itemCost.consumeItems(null, List.of()));
        }

        @Test
        @DisplayName("re-validates with hasItems before consuming; rejects invalid material")
        void revalidatesBeforeConsumingInvalidMaterial() {
            // consumeItems calls hasItems internally first.
            // hasItems returns false for invalid material → consumeItems returns false
            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("INVALID", 1));
            assertFalse(itemCost.consumeItems(null, costs));
        }
    }

    // ── consumeItems with Player mock ────────────────────────

    @Nested
    @DisplayName("consumeItems() with Player inventory")
    class ConsumeItemsWithPlayer {

        private Player player;
        private PlayerInventory inventory;

        @BeforeEach
        void setUp() {
            player = mock(Player.class);
            inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
        }

        @Test
        @DisplayName("consumes items from inventory and returns true")
        void consumesItemsSuccessfully() {
            ItemStack diamond1 = createItemStackStub(Material.DIAMOND, 5);
            when(inventory.getContents())
                    .thenReturn(new ItemStack[]{diamond1})  // first: re-validation
                    .thenReturn(new ItemStack[]{diamond1});  // second: consumption loop

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("DIAMOND", 3));

            assertTrue(itemCost.consumeItems(player, costs));

            // Verify setAmount was called to reduce the item
            verify(diamond1, atLeastOnce()).setAmount(anyInt());
            verify(diamond1, atLeastOnce()).getAmount();
        }

        @Test
        @DisplayName("re-validates before consuming; returns false when items insufficient on re-check")
        void revalidationDetectsInsufficientItems() {
            // Item count was sufficient initially but is insufficient at re-validation time.
            // First getContents (during re-validation inside consumeItems):
            when(inventory.getContents()).thenReturn(new ItemStack[]{});

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("DIAMOND", 3));

            // hasItems returns false → consumeItems returns false, nothing consumed
            assertFalse(itemCost.consumeItems(player, costs));
        }

        @Test
        @DisplayName("skips invalid material entries during consumption")
        void skipsInvalidMaterials() {
            ItemStack diamond1 = createItemStackStub(Material.DIAMOND, 5);
            when(inventory.getContents())
                    .thenReturn(new ItemStack[]{diamond1})  // re-validation: INVALID fails → return false
                    .thenReturn(new ItemStack[]{diamond1});

            List<CostCalculator.ItemCostEntry> costs = List.of(
                    new CostCalculator.ItemCostEntry("INVALID_MATERIAL_XYZ", 1));

            // Invalid material causes hasItems to return false at re-validation
            assertFalse(itemCost.consumeItems(player, costs));
        }
    }

    // ── Material resolution ──────────────────────────────────

    @Nested
    @DisplayName("Material.matchMaterial() behavior")
    class MaterialResolution {

        @Test
        @DisplayName("valid material is resolved correctly")
        void validMaterialResolution() {
            CostCalculator.ItemCostEntry entry = new CostCalculator.ItemCostEntry("DIAMOND", 3);
            assertEquals("DIAMOND", entry.material());
            assertEquals(3, entry.amount());

            Material mat = Material.matchMaterial(entry.material());
            assertNotNull(mat);
            assertEquals("DIAMOND", mat.name());
        }

        @Test
        @DisplayName("invalid material returns null")
        void invalidMaterialReturnsNull() {
            Material mat = Material.matchMaterial("NONEXISTENT_ITEM_12345");
            assertNull(mat);
        }

        @Test
        @DisplayName("material matching is case-insensitive")
        void materialCaseInsensitive() {
            Material lower = Material.matchMaterial("diamond");
            Material upper = Material.matchMaterial("DIAMOND");
            Material mixed = Material.matchMaterial("Diamond");

            assertNotNull(lower);
            assertNotNull(upper);
            assertNotNull(mixed);
            assertEquals(lower, upper);
            assertEquals(upper, mixed);
        }
    }

    // ── Helper: create a stubbed ItemStack ────────────────────

    /**
     * Create a mock ItemStack that returns the given material and amount.
     */
    private static ItemStack createItemStackStub(Material material, int amount) {
        ItemStack item = mock(ItemStack.class);
        lenient().when(item.getType()).thenReturn(material);
        lenient().when(item.getAmount()).thenReturn(amount);
        return item;
    }
}
