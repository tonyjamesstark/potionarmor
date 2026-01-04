# CE Item Interactions - Comprehensive Test Plan

## Setup
1. Create test items with CE lore lines (e.g., sword with "Regeneration" lore, armor with effects)
2. Ensure effects are configured in `config.yml`
3. Have the plugin loaded on a test server
4. Test in both Survival and Creative modes where applicable

## Legend
- **CE activates**: Effect should appear when item reaches destination
- **CE deactivates**: Effect should disappear when item leaves slot
- **CE remains**: Effect should stay active (item moving between valid slots)
- [FIXED] = Previously broken, now fixed
- [OK] = Was already working
- [NEW] = New test case not in original spreadsheet

---

## PRIORITY TESTS (Previously Failing)

### Activation Tests
| # | From | To | Method | Expected | Status |
|---|------|-----|--------|----------|--------|
| 1 | Unequipped | MainHand | Shift move in inventory | CE activates | [FIXED] |
| 2 | Unequipped | MainHand | Number move in inventory | CE activates | [FIXED] |
| 3 | Unequipped | MainHand | Click pick up item - close inv | CE activates | [FIXED] |

### Deactivation Tests
| # | From | To | Method | Expected | Status |
|---|------|-----|--------|----------|--------|
| 4 | MainHand | Unequipped | Number move in inventory | CE deactivates | [FIXED] |
| 5 | MainHand | Unequipped | Block picker move (middle-click) | CE deactivates | [FIXED] |
| 6 | OffHand | Unequipped | Shift move in inventory | CE deactivates | [FIXED] |

### "Remain" Tests (Effect should persist)
| # | From | To | Method | Expected | Status |
|---|------|-----|--------|----------|--------|
| 7 | MainHand | OffHand | Switch hands hotkey (F) | CE remains | [FIXED] |
| 8 | OffHand | MainHand | Switch hands hotkey (F) | CE remains | [FIXED] |
| 9 | MainHand | GearSlot | Shift move in inventory | CE remains | [FIXED] |
| 10 | GearSlot | MainHand | Number move in inv | CE remains | [FIXED] |
| 11 | GearSlot | MainHand | Shift move in inventory | CE remains | [FIXED] |

---

## REGRESSION TESTS (From Original Spreadsheet)

### Unequipped → MainHand
| # | Method | Expected | Status |
|---|--------|----------|--------|
| 12 | Hotbar scroll | CE activates | [OK] |
| 13 | Hotbar number selection | CE activates | [OK] |
| 14 | Click move in inventory | CE activates | [OK] |
| 15 | Item pick-up into MainHand | CE activates | [OK] |

### Unequipped → OffHand
| # | Method | Expected | Status |
|---|--------|----------|--------|
| 16 | Number move in inventory | CE activates | [OK] |
| 17 | Click move in inventory | CE activates | [OK] |

### Unequipped → GearSlot
| # | Method | Expected | Status |
|---|--------|----------|--------|
| 18 | Equip with dispenser | CE activates | [OK] |
| 19 | Number move in inventory | CE activates | [OK] |
| 20 | Click move in inventory | CE activates | [OK] |
| 21 | Shift move in inventory | CE activates | [OK] |

### MainHand → Unequipped
| # | Method | Expected | Status |
|---|--------|----------|--------|
| 22 | Hotbar scroll | CE deactivates | [OK] |
| 23 | Hotbar number selection | CE deactivates | [OK] |
| 24 | Click move in inventory | CE deactivates | [OK] |
| 25 | Shift move in inventory | CE deactivates | [OK] |
| 26 | Drop item with Q | CE deactivates | [OK] |
| 27 | Place in itemframe | CE deactivates | [OK] |

### MainHand → OffHand
| # | Method | Expected | Status |
|---|--------|----------|--------|
| 28 | Number move in inv | CE remains | [OK] |

### MainHand → GearSlot
| # | Method | Expected | Status |
|---|--------|----------|--------|
| 29 | Right click equip | CE remains | [OK] |
| 30 | Number move | CE remains | [OK] |
| 31 | /hat command | CE remains | [OK] |

### OffHand → Unequipped
| # | Method | Expected | Status |
|---|--------|----------|--------|
| 32 | Number move in inv | CE deactivates | [OK] |
| 33 | Drop item with Q in inv | CE deactivates | [OK] |
| 34 | Click move in inv | CE deactivates | [OK] |
| 35 | Place in itemframe | CE deactivates | [OK] |

### OffHand → MainHand
| # | Method | Expected | Status |
|---|--------|----------|--------|
| 36 | Number move in inv | CE remains | [OK] |
| 37 | Shift move in inventory | CE remains | [OK] |

### GearSlot → Unequipped
| # | Method | Expected | Status |
|---|--------|----------|--------|
| 38 | Number move in inv | CE deactivates | [OK] |
| 39 | Drop item with Q in inv | CE deactivates | [OK] |
| 40 | Click move in inv | CE deactivates | [OK] |
| 41 | Shift move in inventory | CE deactivates | [OK] |

---

## ADDITIONAL TESTS (Handled in Code, Not in Original Spreadsheet)

### Player Lifecycle
| # | Scenario | Expected | Status |
|---|----------|----------|--------|
| 42 | Player death (keepInventory=false) | CE deactivates | [NEW] |
| 43 | Player death (keepInventory=true) | CE remains | [NEW] |
| 44 | Player respawn | CE reactivates from equipment | [NEW] |
| 45 | Player join server | CE activates from equipment | [NEW] |
| 46 | Player quit and rejoin | CE activates from equipment | [NEW] |

### World/Gamemode Changes
| # | Scenario | Expected | Status |
|---|----------|----------|--------|
| 47 | Change dimension (Nether portal) | CE remains | [NEW] |
| 48 | Change dimension (End portal) | CE remains | [NEW] |
| 49 | Gamemode change (Survival→Creative) | CE remains | [NEW] |
| 50 | Gamemode change (Creative→Survival) | CE remains | [NEW] |

### Entity Interactions
| # | Scenario | Expected | Status |
|---|----------|----------|--------|
| 51 | Put item on armor stand | CE deactivates | [NEW] |
| 52 | Take item from armor stand | CE activates | [NEW] |
| 53 | Give armor to horse | CE deactivates | [NEW] |
| 54 | Take armor from horse | CE activates | [NEW] |

### Inventory Operations
| # | Scenario | Expected | Status |
|---|----------|----------|--------|
| 55 | Drag-distribute to armor slot | CE activates | [NEW] |
| 56 | Drag-distribute from armor slot | CE deactivates | [NEW] |

---

## MISSING EVENT COVERAGE (Needs Implementation)

### Item Destruction/Consumption (HIGH PRIORITY)
| # | Scenario | Expected | Currently | Event Needed |
|---|----------|----------|-----------|--------------|
| 57 | Tool breaks from durability | CE deactivates | NOT HANDLED | PlayerItemBreakEvent |
| 58 | Armor breaks from durability | CE deactivates | NOT HANDLED | PlayerItemBreakEvent |
| 59 | Eat food in hand | CE deactivates | NOT HANDLED | PlayerItemConsumeEvent |
| 60 | Drink potion in hand | CE deactivates | NOT HANDLED | PlayerItemConsumeEvent |
| 61 | Use milk bucket | CE deactivates | NOT HANDLED | PlayerItemConsumeEvent |

### Throwables (MEDIUM PRIORITY)
| # | Scenario | Expected | Currently | Event Needed |
|---|----------|----------|-----------|--------------|
| 62 | Throw ender pearl | CE deactivates | NOT HANDLED | ProjectileLaunchEvent |
| 63 | Throw snowball | CE deactivates | NOT HANDLED | ProjectileLaunchEvent |
| 64 | Throw egg | CE deactivates | NOT HANDLED | ProjectileLaunchEvent |
| 65 | Throw splash potion | CE deactivates | NOT HANDLED | ProjectileLaunchEvent |
| 66 | Throw lingering potion | CE deactivates | NOT HANDLED | ProjectileLaunchEvent |
| 67 | Throw trident (no Loyalty) | CE deactivates | NOT HANDLED | ProjectileLaunchEvent |

### Commands (LOW PRIORITY - Validation task catches)
| # | Scenario | Expected | Currently | Event Needed |
|---|----------|----------|-----------|--------------|
| 68 | /give item to hand | CE activates | Delayed via validation | PlayerCommandPreprocessEvent |
| 69 | /clear removes item | CE deactivates | Delayed via validation | PlayerCommandPreprocessEvent |

### Creative Mode (LOW PRIORITY)
| # | Scenario | Expected | Currently | Event Needed |
|---|----------|----------|-----------|--------------|
| 70 | Ctrl+middle-click clone | CE activates | NOT HANDLED | CreativeInventoryEvent? |
| 71 | Creative inventory pick | CE activates | NOT HANDLED | InventoryCreativeEvent |

### Crafting/Container Output (LOW PRIORITY)
| # | Scenario | Expected | Currently | Event Needed |
|---|----------|----------|-----------|--------------|
| 72 | Take crafting output to full inv | CE activates if to hand | Partial | InventoryClickEvent |
| 73 | Take anvil output | CE activates if to hand | Partial | InventoryClickEvent |

---

## Test Execution Instructions

### How to perform each method:
- **Hotbar scroll**: Mouse wheel to change selected slot
- **Hotbar number selection**: Press 1-9 keys
- **Click move in inventory**: Left-click item, left-click destination
- **Shift move in inventory**: Shift+left-click item
- **Number move in inventory**: Hover item, press 1-9 to swap with hotbar
- **Drop item with Q**: Press Q while holding/hovering item
- **Switch hands hotkey**: Press F key
- **Block picker move**: Middle-click a block (survival/creative)
- **Click pick up - close inv**: Pick up item with cursor, press E to close
- **Right click equip**: Right-click armor in hand
- **/hat**: Use Essentials /hat command
- **Dispenser equip**: Stand on dispenser, activate with redstone
- **Itemframe place**: Right-click itemframe with item

### What to verify:
1. Effect icon appears/disappears in top-right HUD
2. Actual effect works (e.g., regeneration heals, speed increases movement)
3. No duplicate effects (check with `/effect query`)
4. No effects "stuck" after item removed
5. Effect level correct when multiple items have same effect type

---

## Quick Smoke Test (5 min)
If short on time, test these critical paths:
1. [ ] Test #1: Shift-move to MainHand (was broken)
2. [ ] Test #7: F-key swap MainHand↔OffHand (was broken)
3. [ ] Test #5: Block picker (was broken)
4. [ ] Test #12: Hotbar scroll (regression check)
5. [ ] Test #22: Hotbar scroll away (regression check)

## Full Test Checklist
- [ ] Tests 1-11: Priority fixes
- [ ] Tests 12-41: Original spreadsheet regression
- [ ] Tests 42-56: Additional scenarios in code
- [ ] Tests 57-73: After implementing missing events

---

## Event Handler Coverage Matrix

| Event | Handler | Status |
|-------|---------|--------|
| PlayerArmorChangeEvent | changeArmor() | ✓ Implemented |
| InventoryClickEvent | invClick() | ✓ Implemented |
| PlayerItemHeldEvent | newItemHeld() | ✓ Implemented |
| PlayerDropItemEvent | drop() | ✓ Implemented |
| PlayerSwapHandItemsEvent | swapHands() | ✓ Implemented |
| EntityPickupItemEvent | pickup() | ✓ Implemented |
| InventoryCloseEvent | inventoryClose() | ✓ Implemented |
| PlayerPickItemEvent | blockPick() | ✓ Implemented |
| BlockDispenseArmorEvent | dispenserArmor() | ✓ Implemented |
| PlayerArmorStandManipulateEvent | armorStandInteract() | ✓ Implemented |
| PlayerInteractEntityEvent | entityInteract() | ✓ Implemented |
| InventoryDragEvent | inventoryDrag() | ✓ Implemented |
| PlayerDeathEvent | playerDeath() | ✓ Implemented |
| PlayerRespawnEvent | playerRespawn() | ✓ Implemented |
| PlayerJoinEvent | playerJoin() | ✓ Implemented |
| PlayerQuitEvent | playerQuit() | ✓ Implemented |
| PlayerChangedWorldEvent | changeWorld() | ✓ Implemented |
| PlayerGameModeChangeEvent | gamemode() | ✓ Implemented |
| PlayerCommandPreprocessEvent | hatPostCheck() | ✓ Partial (/hat only) |
| PlayerItemBreakEvent | - | ❌ NOT IMPLEMENTED |
| PlayerItemConsumeEvent | - | ❌ NOT IMPLEMENTED |
| ProjectileLaunchEvent | - | ❌ NOT IMPLEMENTED |
| InventoryCreativeEvent | - | ❌ NOT IMPLEMENTED |
