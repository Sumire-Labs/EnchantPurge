package com.example.enchantpurge.core;

import com.example.enchantpurge.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EnchantmentFilter}.
 * <p>
 * Tests null safety, configuration refresh, and {@code isAllowed()} logic
 * using Mockito-mocked {@link ItemStack} and {@link MockedStatic} for
 * {@link Material#matchMaterial(String)}.
 * <p>
 * We cannot stub {@code Material.equals()/hashCode()} (Mockito limitation).
 * Instead, we mock {@code matchMaterial()} to return mock {@link Material}
 * instances. The same mock objects are stored in {@code effectiveMaterials}
 * and returned by {@code item.getType()}, so {@code List.contains()} works
 * via reference equality.
 * <p>
 * <b>Limitation:</b> The {@code == Material.ENCHANTED_BOOK} reference equality
 * check cannot be triggered with mock Materials. ENCHANTED_BOOK-specific
 * logic is verified through config refresh and code review.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnchantmentFilter")
class EnchantmentFilterTest {

    @Mock
    private ConfigManager configManager;

    @Mock
    private Logger logger;

    @Mock
    private ItemStack item;

    private EnchantmentFilter filter;

    // Per-nested-class mock Material instances and static mock
    private MockedStatic<Material> materialStatic;

    @BeforeEach
    void setUp() {
        lenient().doReturn("blacklist").when(configManager).getTargetMode();
        lenient().doReturn(List.of()).when(configManager).getBlacklist();
        lenient().doReturn(List.of()).when(configManager).getWhitelist();
        lenient().doReturn(false).when(configManager).isIncludeEnchantedBooks();

        // Default: Material.matchMaterial returns null for everything
        // (overridden in nested @BeforeEach for specific tests)
        materialStatic = mockStatic(Material.class);
        materialStatic.when(() -> Material.matchMaterial(anyString())).thenReturn(null);

        filter = new EnchantmentFilter(configManager, logger);
    }

    @AfterEach
    void tearDown() {
        if (materialStatic != null) {
            materialStatic.close();
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    /**
     * Register mock Materials with matchMaterial so refreshMaterials()
     * populates effectiveMaterials with mock objects.
     */
    private void registerMaterial(String name, Material mockMat) {
        materialStatic.when(() -> Material.matchMaterial(name)).thenReturn(mockMat);
    }

    private void stubItemMaterial(Material mockMat) {
        doReturn(mockMat).when(item).getType();
    }

    private void configureBlacklist(List<String> materials) {
        lenient().doReturn("blacklist").when(configManager).getTargetMode();
        lenient().doReturn(materials).when(configManager).getBlacklist();
        lenient().doReturn(List.of()).when(configManager).getWhitelist();
        filter.refreshMaterials();
    }

    private void configureWhitelist(List<String> materials) {
        lenient().doReturn("whitelist").when(configManager).getTargetMode();
        lenient().doReturn(List.of()).when(configManager).getBlacklist();
        lenient().doReturn(materials).when(configManager).getWhitelist();
        filter.refreshMaterials();
    }

    // ── null / air handling ──────────────────────────────────

    @Nested
    @DisplayName("null item handling")
    class NullItem {

        @Test
        @DisplayName("null item is not allowed")
        void nullItemNotAllowed() {
            assertFalse(filter.isAllowed(null));
        }
    }

    @Nested
    @DisplayName("air item handling")
    class AirItem {

        @Test
        @DisplayName("item with getType().isAir() = true is not allowed")
        void airItemNotAllowed() {
            Material mockAir = mock(Material.class);
            doReturn(true).when(mockAir).isAir();
            doReturn(mockAir).when(item).getType();

            assertFalse(filter.isAllowed(item));
        }
    }

    // ── Configuration refresh ────────────────────────────────

    @Nested
    @DisplayName("configuration refresh")
    class ConfigurationRefresh {

        @Test
        @DisplayName("constructor with empty config does not throw")
        void constructorDoesNotThrow() {
            assertNotNull(filter);
        }

        @Test
        @DisplayName("refreshMaterials with empty config does not throw")
        void refreshMaterialsDoesNotThrow() {
            assertDoesNotThrow(() -> filter.refreshMaterials());
        }

        @Test
        @DisplayName("getConfiguredMaterials returns empty unmodifiable list by default")
        void defaultEmptyList() {
            var materials = filter.getConfiguredMaterials();
            assertTrue(materials.isEmpty());
            assertThrows(UnsupportedOperationException.class,
                    () -> materials.add(Material.DIAMOND));
        }

        @Test
        @DisplayName("invalid material names are logged as warnings and skipped")
        void invalidMaterialSkipped() {
            // Register a valid material via mock
            Material mockSword = mock(Material.class);
            registerMaterial("DIAMOND_SWORD", mockSword);
            configureBlacklist(List.of("INVALID_MATERIAL_XYZ", "DIAMOND_SWORD"));

            var materials = filter.getConfiguredMaterials();
            assertEquals(1, materials.size());
            assertTrue(materials.contains(mockSword));
            verify(logger, atLeastOnce()).warning(contains("INVALID_MATERIAL_XYZ"));
        }
    }

    // ── isAllowed: blacklist mode ─────────────────────────────

    @Nested
    @DisplayName("isAllowed - blacklist mode")
    class BlacklistMode {

        private Material mockDiamondSword;
        private Material mockElytra;
        private Material mockBow;

        @BeforeEach
        void setUp() {
            mockDiamondSword = mock(Material.class);
            mockElytra = mock(Material.class);
            mockBow = mock(Material.class);

            registerMaterial("DIAMOND_SWORD", mockDiamondSword);
            registerMaterial("ELYTRA", mockElytra);
            registerMaterial("BOW", mockBow);

            configureBlacklist(List.of("DIAMOND_SWORD", "ELYTRA"));
        }

        @Test
        @DisplayName("material in blacklist is rejected")
        void blacklistedMaterialRejected() {
            stubItemMaterial(mockDiamondSword);
            assertFalse(filter.isAllowed(item));
        }

        @Test
        @DisplayName("material not in blacklist is allowed")
        void nonBlacklistedMaterialAllowed() {
            stubItemMaterial(mockBow);
            assertTrue(filter.isAllowed(item));
        }

        @Test
        @DisplayName("ELYTRA in blacklist is rejected when item is ELYTRA")
        void elytraRejected() {
            stubItemMaterial(mockElytra);
            assertFalse(filter.isAllowed(item));
        }
    }

    // ── isAllowed: whitelist mode ─────────────────────────────

    @Nested
    @DisplayName("isAllowed - whitelist mode")
    class WhitelistMode {

        private Material mockDiamondSword;
        private Material mockBow;
        private Material mockElytra;

        @BeforeEach
        void setUp() {
            mockDiamondSword = mock(Material.class);
            mockBow = mock(Material.class);
            mockElytra = mock(Material.class);

            registerMaterial("DIAMOND_SWORD", mockDiamondSword);
            registerMaterial("BOW", mockBow);
            registerMaterial("ELYTRA", mockElytra);

            configureWhitelist(List.of("DIAMOND_SWORD", "BOW"));
        }

        @Test
        @DisplayName("material in whitelist is allowed")
        void whitelistedMaterialAllowed() {
            stubItemMaterial(mockDiamondSword);
            assertTrue(filter.isAllowed(item));
        }

        @Test
        @DisplayName("material not in whitelist is rejected")
        void nonWhitelistedMaterialRejected() {
            stubItemMaterial(mockElytra);
            assertFalse(filter.isAllowed(item));
        }
    }

    // ── isAllowed: empty list ─────────────────────────────────

    @Nested
    @DisplayName("isAllowed - empty material list")
    class EmptyList {

        private Material mockAny;

        @BeforeEach
        void setUp() {
            mockAny = mock(Material.class);
            configureBlacklist(List.of());
        }

        @Test
        @DisplayName("any non-book non-air item passes when list is empty")
        void emptyListAllAllowed() {
            stubItemMaterial(mockAny);
            assertTrue(filter.isAllowed(item));
        }
    }

    // ── ENCHANTED_BOOK handling ───────────────────────────────
    //
    // isAllowed() uses reference equality (== Material.ENCHANTED_BOOK).
    // This cannot be triggered with mock Material instances.
    // ENCHANTED_BOOK-specific logic is verified through:
    //   - Code review of isAllowed() method
    //   - ConfigManager.isIncludeEnchantedBooks() defaults
    //   - Integration tests (MockBukkit) — phase 6 / future
}
