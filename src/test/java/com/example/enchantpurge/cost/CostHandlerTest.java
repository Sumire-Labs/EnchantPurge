package com.example.enchantpurge.cost;

import com.example.enchantpurge.core.CostCalculator;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CostHandler}.
 * <p>
 * Tests the full cost validation and consumption flow using mock cost handlers
 * and a mock Player, without requiring a Paper server runtime.
 * Verifies: affordability checks, consumption order, and atomicity (no partial
 * consumption on failure).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CostHandler")
class CostHandlerTest {

    @Mock
    private ExpCost expCost;

    @Mock
    private MoneyCost moneyCost;

    @Mock
    private ItemCost itemCost;

    @Mock
    private Player player;

    private CostHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CostHandler(expCost, moneyCost, itemCost);
    }

    // ── AffordResult enum ─────────────────────────────────────

    @Nested
    @DisplayName("AffordResult enum")
    class AffordResultEnum {

        @Test
        @DisplayName("OK.isOk() returns true")
        void okIsOk() {
            assertTrue(CostHandler.AffordResult.OK.isOk());
        }

        @Test
        @DisplayName("non-OK.isOk() returns false")
        void nonOkIsNotOk() {
            assertFalse(CostHandler.AffordResult.INSUFFICIENT_EXP.isOk());
            assertFalse(CostHandler.AffordResult.INSUFFICIENT_MONEY.isOk());
            assertFalse(CostHandler.AffordResult.INSUFFICIENT_ITEMS.isOk());
        }

        @Test
        @DisplayName("each result has correct message key")
        void distinctMessageKeys() {
            assertNull(CostHandler.AffordResult.OK.getMessageKey());
            assertEquals("messages.insufficient_exp",
                    CostHandler.AffordResult.INSUFFICIENT_EXP.getMessageKey());
            assertEquals("messages.insufficient_money",
                    CostHandler.AffordResult.INSUFFICIENT_MONEY.getMessageKey());
            assertEquals("messages.insufficient_items",
                    CostHandler.AffordResult.INSUFFICIENT_ITEMS.getMessageKey());
        }
    }

    // ── FREE cost handling ───────────────────────────────────

    @Nested
    @DisplayName("FREE cost handling")
    class FreeCostHandling {

        @Test
        @DisplayName("CostResult.FREE is free")
        void freeIsFree() {
            assertTrue(CostCalculator.CostResult.FREE.isFree());
        }

        @Test
        @DisplayName("CostResult.FREE has zero values")
        void freeHasZeroValues() {
            assertEquals(0, CostCalculator.CostResult.FREE.expLevels());
            assertEquals(0.0, CostCalculator.CostResult.FREE.money());
            assertTrue(CostCalculator.CostResult.FREE.itemCosts().isEmpty());
        }
    }

    // ── canAffordAll ─────────────────────────────────────────

    @Nested
    @DisplayName("canAffordAll")
    class CanAffordAll {

        private CostCalculator.CostResult cost;

        @BeforeEach
        void setUp() {
            cost = new CostCalculator.CostResult(5, 100.0,
                    List.of(new CostCalculator.ItemCostEntry("DIAMOND", 2)));
        }

        @Test
        @DisplayName("returns OK when all costs are affordable")
        void allAffordableReturnsOk() {
            when(expCost.hasEnough(player, 5)).thenReturn(true);
            when(moneyCost.hasEnough(player, 100.0)).thenReturn(true);
            when(itemCost.hasItems(player, cost.itemCosts())).thenReturn(true);

            CostHandler.AffordResult result = handler.canAffordAll(player, cost);

            assertEquals(CostHandler.AffordResult.OK, result);
        }

        @Test
        @DisplayName("returns OK for FREE cost without checking")
        void freeCostReturnsOk() {
            CostHandler.AffordResult result = handler.canAffordAll(player,
                    CostCalculator.CostResult.FREE);

            assertEquals(CostHandler.AffordResult.OK, result);
            verifyNoInteractions(expCost);
            verifyNoInteractions(moneyCost);
            verifyNoInteractions(itemCost);
        }

        @Test
        @DisplayName("returns INSUFFICIENT_EXP when exp insufficient")
        void insufficientExp() {
            when(expCost.hasEnough(player, 5)).thenReturn(false);

            CostHandler.AffordResult result = handler.canAffordAll(player, cost);

            assertEquals(CostHandler.AffordResult.INSUFFICIENT_EXP, result);
            // Money/item not checked on exp failure
            verifyNoInteractions(moneyCost);
            verifyNoInteractions(itemCost);
        }

        @Test
        @DisplayName("returns INSUFFICIENT_MONEY when money insufficient")
        void insufficientMoney() {
            // Exp ok, money not ok
            when(expCost.hasEnough(player, 5)).thenReturn(true);
            when(moneyCost.hasEnough(player, 100.0)).thenReturn(false);

            CostHandler.AffordResult result = handler.canAffordAll(player, cost);

            assertEquals(CostHandler.AffordResult.INSUFFICIENT_MONEY, result);
            // Items not checked on money failure
            verifyNoInteractions(itemCost);
        }

        @Test
        @DisplayName("returns INSUFFICIENT_ITEMS when items insufficient")
        void insufficientItems() {
            // Exp ok, money ok, items not ok
            when(expCost.hasEnough(player, 5)).thenReturn(true);
            when(moneyCost.hasEnough(player, 100.0)).thenReturn(true);
            when(itemCost.hasItems(player, cost.itemCosts())).thenReturn(false);

            CostHandler.AffordResult result = handler.canAffordAll(player, cost);

            assertEquals(CostHandler.AffordResult.INSUFFICIENT_ITEMS, result);
        }

        @Test
        @DisplayName("check order is exp → money → items")
        void checkOrder() {
            when(expCost.hasEnough(any(), anyInt())).thenReturn(true);
            when(moneyCost.hasEnough(any(), anyDouble())).thenReturn(true);
            when(itemCost.hasItems(any(), anyList())).thenReturn(true);

            handler.canAffordAll(player, cost);

            InOrder order = inOrder(expCost, moneyCost, itemCost);
            order.verify(expCost).hasEnough(player, 5);
            order.verify(moneyCost).hasEnough(player, 100.0);
            order.verify(itemCost).hasItems(player, cost.itemCosts());
        }
    }

    // ── consumeAll ───────────────────────────────────────────

    @Nested
    @DisplayName("consumeAll")
    class ConsumeAll {

        private CostCalculator.CostResult cost;

        @BeforeEach
        void setUp() {
            cost = new CostCalculator.CostResult(5, 100.0,
                    List.of(new CostCalculator.ItemCostEntry("DIAMOND", 2)));
        }

        @Test
        @DisplayName("returns true for FREE cost without consuming anything")
        void freeCostNoConsumption() {
            assertTrue(handler.consumeAll(player, CostCalculator.CostResult.FREE));
            verifyNoInteractions(expCost);
            verifyNoInteractions(moneyCost);
            verifyNoInteractions(itemCost);
        }

        @Test
        @DisplayName("consumes all costs in correct order: money → items → exp")
        void consumeAllOrder() {
            when(moneyCost.deduct(player, 100.0)).thenReturn(true);
            when(itemCost.consumeItems(player, cost.itemCosts())).thenReturn(true);

            assertTrue(handler.consumeAll(player, cost));

            InOrder order = inOrder(moneyCost, itemCost, expCost);
            order.verify(moneyCost).deduct(player, 100.0);
            order.verify(itemCost).consumeItems(player, cost.itemCosts());
            order.verify(expCost).deduct(player, 5);
        }

        @Test
        @DisplayName("money failure prevents item and exp consumption")
        void moneyFailurePreventsAll() {
            when(moneyCost.deduct(player, 100.0)).thenReturn(false);

            assertFalse(handler.consumeAll(player, cost));

            // Items and exp NOT consumed
            verifyNoInteractions(itemCost);
            verifyNoInteractions(expCost);
        }

        @Test
        @DisplayName("item failure prevents exp consumption")
        void itemFailurePreventsExp() {
            when(moneyCost.deduct(player, 100.0)).thenReturn(true);
            when(itemCost.consumeItems(player, cost.itemCosts())).thenReturn(false);

            assertFalse(handler.consumeAll(player, cost));

            // Money was consumed (no rollback)
            verify(moneyCost).deduct(player, 100.0);
            // Exp NOT consumed
            verifyNoInteractions(expCost);
        }

        @Test
        @DisplayName("skips money when amount is zero")
        void skipsZeroMoney() {
            CostCalculator.CostResult noMoney =
                    new CostCalculator.CostResult(5, 0.0, cost.itemCosts());
            when(itemCost.consumeItems(player, noMoney.itemCosts())).thenReturn(true);

            assertTrue(handler.consumeAll(player, noMoney));

            verifyNoInteractions(moneyCost);
            verify(itemCost).consumeItems(player, noMoney.itemCosts());
            verify(expCost).deduct(player, 5);
        }

        @Test
        @DisplayName("skips items when list is empty")
        void skipsEmptyItems() {
            CostCalculator.CostResult noItems =
                    new CostCalculator.CostResult(5, 100.0, List.of());
            when(moneyCost.deduct(player, 100.0)).thenReturn(true);

            assertTrue(handler.consumeAll(player, noItems));

            verify(moneyCost).deduct(player, 100.0);
            verifyNoInteractions(itemCost);
            verify(expCost).deduct(player, 5);
        }

        @Test
        @DisplayName("skips exp when levels is zero")
        void skipsZeroExp() {
            CostCalculator.CostResult noExp =
                    new CostCalculator.CostResult(0, 100.0, cost.itemCosts());
            when(moneyCost.deduct(player, 100.0)).thenReturn(true);
            when(itemCost.consumeItems(player, noExp.itemCosts())).thenReturn(true);

            assertTrue(handler.consumeAll(player, noExp));

            verify(moneyCost).deduct(player, 100.0);
            verify(itemCost).consumeItems(player, noExp.itemCosts());
            verifyNoInteractions(expCost);
        }
    }

    // ── checkAndConsume ──────────────────────────────────────

    @Nested
    @DisplayName("checkAndConsume")
    class CheckAndConsume {

        private CostCalculator.CostResult cost;

        @BeforeEach
        void setUp() {
            cost = new CostCalculator.CostResult(5, 100.0,
                    List.of(new CostCalculator.ItemCostEntry("DIAMOND", 2)));
        }

        @Test
        @DisplayName("returns OK and consumes when affordable")
        void affordableConsumesAndReturnsOk() {
            when(expCost.hasEnough(player, 5)).thenReturn(true);
            when(moneyCost.hasEnough(player, 100.0)).thenReturn(true);
            when(itemCost.hasItems(player, cost.itemCosts())).thenReturn(true);
            when(moneyCost.deduct(player, 100.0)).thenReturn(true);
            when(itemCost.consumeItems(player, cost.itemCosts())).thenReturn(true);

            CostHandler.AffordResult result = handler.checkAndConsume(player, cost);

            assertEquals(CostHandler.AffordResult.OK, result);
            verify(expCost).deduct(player, 5);
        }

        @Test
        @DisplayName("returns error without consuming when cannot afford")
        void cannotAffordNoConsumption() {
            when(expCost.hasEnough(player, 5)).thenReturn(false);

            CostHandler.AffordResult result = handler.checkAndConsume(player, cost);

            assertEquals(CostHandler.AffordResult.INSUFFICIENT_EXP, result);
            // Nothing should be consumed
            verifyNoInteractions(moneyCost);
            verifyNoInteractions(itemCost);
            verify(expCost, never()).deduct(any(), anyInt());
        }

        @Test
        @DisplayName("returns INSUFFICIENT_MONEY when consumeAll fails")
        void consumeFailsReturnsMoneyError() {
            // All affordability checks pass
            when(expCost.hasEnough(player, 5)).thenReturn(true);
            when(moneyCost.hasEnough(player, 100.0)).thenReturn(true);
            when(itemCost.hasItems(player, cost.itemCosts())).thenReturn(true);
            // But money deduction fails
            when(moneyCost.deduct(player, 100.0)).thenReturn(false);

            CostHandler.AffordResult result = handler.checkAndConsume(player, cost);

            assertEquals(CostHandler.AffordResult.INSUFFICIENT_MONEY, result);
            // Exp never deducted
            verify(expCost, never()).deduct(any(), anyInt());
        }
    }
}
