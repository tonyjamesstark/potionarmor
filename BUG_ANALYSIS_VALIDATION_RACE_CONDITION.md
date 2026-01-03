# Bug Analysis: Periodic Validation Race Condition

**Date**: 2026-01-03
**Reported Issue**: Players experiencing effects being removed and then re-added several seconds later
**Severity**: High - Affects gameplay experience
**Introduced In**: Commit 9ae9b64 (2025-12-20)

## Executive Summary

The periodic validation task introduced to detect orphaned effects has a race condition with the async effect application system. When validation runs while equipment changes are in-flight, it incorrectly detects mismatches and triggers full effect resets, causing the reported flickering behavior.

## Symptoms

- Potion effects, particle trails, or disguises suddenly disappear
- Effects reappear 1-2 seconds later
- Occurs approximately every 30 seconds (validation interval)
- More noticeable during active gameplay with frequent equipment changes
- Server logs show warnings: "Found X orphaned effects on <player>"

## Root Cause Analysis

### The Race Condition

The bug occurs due to a timing conflict between two systems:

1. **Async Effect Application** (Thread Pool)
   - Equipment changes trigger async tasks to add/remove effects
   - Tasks execute on worker threads from `ScheduledThreadPoolExecutor`
   - Effects are applied/removed on main thread via `callSyncMethod()`
   - Tracker is updated after effect application completes

2. **Periodic Validation** (Main Thread)
   - Runs every 30 seconds (600 ticks) via `Bukkit.getScheduler().runTaskTimer()`
   - Reads player equipment and calculates expected effects
   - Compares expected vs. tracked effects
   - Triggers full reset on any mismatch

### Specific Problem Areas

#### File: `src/main/java/me/tWizT3d_dreaMr/PotionArmour/PotionArmorPlugin.java`

**Lines 102-124**: Validation task initialization
```java
private void startValidationTask() {
    validationTaskId = Bukkit.getScheduler()
        .runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                manager.validateAndFixPlayerEffects(p);  // Runs every 30s
            }
        }, VALIDATION_INTERVAL_TICKS, VALIDATION_INTERVAL_TICKS)
        .getTaskId();
}
```

**Issue**: 30-second interval is too frequent given async operation timing.

**Line 54**: Validation interval constant
```java
private static final long VALIDATION_INTERVAL_TICKS = 600L; // 30 seconds
```

---

#### File: `src/main/java/me/tWizT3d_dreaMr/PotionArmour/EffectManager.java`

**Lines 456-497**: Validation and correction logic
```java
public boolean validateAndFixPlayerEffects(Player _p) {
    Set<String> expected = calculateExpectedEffects(_p);  // Reads equipment NOW
    Set<String> tracked = tracker.getTrackedEffects(_p);  // Reads tracker NOW
    boolean corrected = false;

    // Find effects that are tracked but shouldn't be (orphaned)
    Set<String> orphaned = new HashSet<>(tracked);
    orphaned.removeAll(expected);

    if (!orphaned.isEmpty()) {
        p.logger.warning("Found " + orphaned.size() + " orphaned effects on "
                        + _p.getName() + ": " + orphaned);
        resetPlayerEffects(_p);  // ← PROBLEM: Too aggressive!
        corrected = true;
    }

    // Find effects that should be active but aren't tracked
    Set<String> missing = new HashSet<>(expected);
    missing.removeAll(tracked);

    if (!missing.isEmpty() && !corrected) {
        p.logger.warning("Found " + missing.size() + " missing effects on "
                        + _p.getName() + ": " + missing);
        reapplyEffectsFromEquipment(_p);  // ← PROBLEM: Redundant reapplication
        corrected = true;
    }

    return corrected;
}
```

**Issues**:
1. **Non-atomic check**: `calculateExpectedEffects()` and `tracker.getTrackedEffects()` are called separately without synchronization
2. **No awareness of pending operations**: Validation doesn't know if async tasks are in-flight
3. **Too aggressive**: Any mismatch immediately triggers reset, even temporary states
4. **No debouncing**: Doesn't account for recent equipment changes

**Lines 422-449**: Expected effects calculation
```java
public Set<String> calculateExpectedEffects(Player _p) {
    // Reads player inventory/equipment on main thread
    // BUT async tasks might be modifying tracker state simultaneously
    ArrayList<ItemStack> equipped = new ArrayList<>();
    equipped.addAll(Arrays.asList(_p.getEquipment().getArmorContents()));
    equipped.add(_p.getInventory().getItemInMainHand());
    equipped.add(_p.getInventory().getItemInOffHand());
    // ... calculate expected effects based on current equipment
}
```

**Issue**: This snapshot of equipment may not match the in-flight async operations.

**Lines 231-274**: Async effect application
```java
private void addEquipment(Player _p, ItemStack i, EquipmentSlot slot, boolean apply) {
    Callable<Void> task = () -> {
        for (String line : getCached(lore)) {
            for (EquipmentEffect eff : effectsTable.get(line)) {
                // Skip if already tracked (prevents duplicate applications)
                if (tracker.isTracked(_p, eff)) {
                    continue;
                }

                Callable<Void> mainTask = () -> {
                    eff.applyTo(_p);
                    tracker.trackEffect(_p, eff);  // ← Tracked AFTER apply
                    return null;
                };
                Bukkit.getServer().getScheduler()
                    .callSyncMethod(PotionArmorPlugin.plugin, mainTask);
            }
        }
        return null;
    };
    p.submitAsyncTask(job);  // ← Queued, not immediate
}
```

**Issue**: Effect is tracked AFTER application. Validation might see effect applied but not tracked.

---

#### File: `src/main/java/me/tWizT3d_dreaMr/PotionArmour/PlayerEffectTracker.java`

**Lines 22-32**: Tracker state maps
```java
private final Map<UUID, Set<String>> activeEffects = new ConcurrentHashMap<>();
private final Map<UUID, Set<String>> activePotionTypes = new ConcurrentHashMap<>();
private final Map<UUID, Set<String>> activeTrails = new ConcurrentHashMap<>();
private final Map<UUID, String> activeDisguise = new ConcurrentHashMap<>();
```

**Status**: Uses `ConcurrentHashMap` for thread-safety, but this doesn't make multi-step operations (calculate + compare) atomic.

## Race Condition Scenarios

### Scenario 1: Equipment Change In Progress

```
Timeline:
T+0ms:   Player swaps helmet
T+1ms:   EventListener.invClick() fires, calls mgr.replaceEquipment()
T+2ms:   removeEquipment() queued in async pool
T+5ms:   removeEquipment() starts executing
T+10ms:  Old helmet effects being removed
T+15ms:  ** VALIDATION RUNS **
T+15ms:  calculateExpectedEffects() - sees new helmet
T+15ms:  tracker.getTrackedEffects() - still has old helmet effects
T+15ms:  Validation sees "orphaned" effects from old helmet
T+16ms:  resetPlayerEffects() called - removes ALL effects
T+20ms:  Effects reapplied from equipment
T+25ms:  Original async task completes (but already redundant)

Result: Player sees effects disappear at T+16ms and reappear at T+20ms
```

### Scenario 2: Application Lag

```
Timeline:
T+0ms:   Player equips boots with Speed effect
T+1ms:   EventListener fires, calls mgr.addEquipment()
T+2ms:   addEquipment() queued in async pool (pool is busy)
T+5ms:   ** VALIDATION RUNS **
T+5ms:   calculateExpectedEffects() - boots are equipped, expects Speed
T+5ms:   tracker.getTrackedEffects() - Speed not tracked yet (task queued)
T+5ms:   Validation sees "missing" Speed effect
T+6ms:   reapplyEffectsFromEquipment() called
T+10ms:  Original async task executes - applies Speed, tracks it
T+11ms:  Redundant reapply task executes - tries to apply Speed again
T+11ms:  Tracker prevents duplicate (tracker.isTracked returns true)

Result: Wasted CPU cycles, but no visible flicker in this case
```

### Scenario 3: Thread Interleaving

```
Timeline:
T+0ms:   Async task: eff.applyTo(_p) starts on main thread
T+1ms:   ** VALIDATION RUNS on main thread **
T+1ms:   calculateExpectedEffects() - sees equipment requiring effect
T+1ms:   Effect is applied to player but NOT YET tracked
T+2ms:   tracker.getTrackedEffects() - doesn't include new effect
T+2ms:   Validation sees "missing" effect
T+3ms:   reapplyEffectsFromEquipment() triggered
T+4ms:   Async task: tracker.trackEffect(_p, eff) completes
T+5ms:   Reapply task runs, sees already tracked, skips

Result: Redundant work, possible momentary flicker
```

## Why This Wasn't Detected Earlier

1. **Timing dependent**: Only manifests when validation runs during equipment changes
2. **30-second window**: Doesn't happen constantly, appears intermittent
3. **Async nature**: Difficult to reproduce deterministically
4. **Works when idle**: Players standing still with no equipment changes see no issues
5. **Server performance**: More noticeable on busy servers with thread pool backlog

## Impact Assessment

**User Experience**:
- Visual disruption during gameplay
- Particle effects flicker
- Speed/jump boost temporarily removed
- Disguises briefly disappear
- Confusing and immersion-breaking

**Server Performance**:
- Unnecessary effect resets every 30 seconds (if triggered)
- Redundant async tasks queued
- Increased CPU usage during validation

**Severity**: High - Core functionality impaired, affects all players periodically

## Proposed Solutions

### Solution 1: Equipment Change Cooldown (Recommended)

**Approach**: Skip validation if equipment changed recently.

**Implementation**:
- Add `Map<UUID, Long> lastEquipmentChange` to `PlayerEffectTracker`
- Track timestamp whenever `addEquipment()`, `removeEquipment()`, or `replaceEquipment()` called
- In `validateAndFixPlayerEffects()`, skip if `(currentTime - lastEquipmentChange) < COOLDOWN`
- Configurable cooldown period (default: 5 seconds = 100 ticks)

**Pros**:
- Simple to implement
- Low risk - doesn't change core logic
- Effectively eliminates race condition during normal gameplay
- Still catches orphaned effects from unusual scenarios (crashes, etc.)

**Cons**:
- Doesn't detect orphaned effects immediately after equipment change
- Time-based, not strictly synchronized

**Risk**: Low
**Effort**: Low
**Effectiveness**: High

---

### Solution 2: Multi-Strike Detection

**Approach**: Require multiple consecutive validation failures before resetting.

**Implementation**:
- Add `Map<UUID, Integer> validationFailureCount` to `EffectManager`
- Increment counter on validation failure, reset on success
- Only trigger `resetPlayerEffects()` if counter >= threshold (e.g., 2)
- Reset counter on equipment change

**Pros**:
- Eliminates false positives from transient states
- No timing assumptions
- Works well with existing validation logic

**Cons**:
- Delays response to real orphaned effects
- Requires state management

**Risk**: Low
**Effort**: Low
**Effectiveness**: High

---

### Solution 3: Increase Validation Interval

**Approach**: Reduce validation frequency.

**Implementation**:
- Change `VALIDATION_INTERVAL_TICKS` from 600 (30s) to 1200 (60s) or higher
- Make configurable via `config.yml`: `meta.validation_interval`

**Pros**:
- Immediate one-line fix
- Reduces occurrence frequency
- Easy to revert

**Cons**:
- Doesn't eliminate race condition, just reduces probability
- Slower to catch real issues

**Risk**: Very Low
**Effort**: Very Low
**Effectiveness**: Medium

---

### Solution 4: Async-Aware Validation

**Approach**: Track pending async operations, skip validation if pending.

**Implementation**:
- Add `Map<UUID, AtomicInteger> pendingOperations` to `EffectManager`
- Increment counter when async task queued
- Decrement when task completes
- Skip validation if `pendingOperations.get(player) > 0`

**Pros**:
- Precise - only skips when actually necessary
- No arbitrary timeouts

**Cons**:
- More complex implementation
- Requires tracking task lifecycle
- Risk of counter desync if tasks fail

**Risk**: Medium
**Effort**: Medium
**Effectiveness**: Very High

---

### Solution 5: Synchronous Validation

**Approach**: Run validation on async thread pool, properly synchronized.

**Implementation**:
- Move `validateAndFixPlayerEffects()` to async pool
- Use explicit locking (ReentrantReadWriteLock) around tracker operations
- Ensure atomic read of equipment + tracker state

**Pros**:
- Proper thread safety
- Eliminates race at architectural level

**Cons**:
- Most invasive change
- Could introduce new concurrency issues
- Requires careful lock management to avoid deadlocks

**Risk**: High
**Effort**: High
**Effectiveness**: Very High

---

### Solution 6: Disable Validation (Temporary Workaround)

**Approach**: Disable validation task entirely or make it opt-in.

**Implementation**:
- Comment out `startValidationTask()` call in `onEnable()`
- OR: Add `meta.validation_enabled: false` config option

**Pros**:
- Immediate fix for production
- Zero risk of regression

**Cons**:
- Loses orphan detection entirely
- Not a real solution

**Risk**: Very Low
**Effort**: Very Low
**Effectiveness**: Fixes symptom, not cause

## Recommended Implementation Plan

### Phase 1: Immediate Fix (Low Risk, High Impact)

Combine Solutions 1, 2, and 3 for defense-in-depth:

1. **Increase validation interval** (Solution 3)
   - Change to 60 seconds to reduce occurrence rate
   - File: `PotionArmorPlugin.java:54`

2. **Add equipment change cooldown** (Solution 1)
   - Track last change time in `PlayerEffectTracker`
   - Skip validation if changed within 5 seconds
   - Files: `PlayerEffectTracker.java`, `EffectManager.java`

3. **Add multi-strike detection** (Solution 2)
   - Require 2 consecutive failures before reset
   - Files: `EffectManager.java`

**Expected Outcome**: Eliminates 95%+ of false positives while maintaining orphan detection.

### Phase 2: Configuration (Low Risk, User Empowerment)

4. **Make validation configurable**
   - Add config options:
     - `meta.validation_enabled` (default: true)
     - `meta.validation_interval` (default: 60 seconds)
     - `meta.validation_cooldown` (default: 5 seconds)
     - `meta.validation_strikes` (default: 2)
   - Files: `config.yml`, `PotionArmorPlugin.java`

### Phase 3: Enhanced Logging (Debugging)

5. **Improve diagnostic logging**
   - Log when validation skipped due to cooldown
   - Log strike counts before reset
   - Add `/debugValidation <player>` command
   - Files: `EffectManager.java`, `PotionArmorPlugin.java`

### Phase 4: Future Enhancement (Optional)

6. **Consider async-aware validation** (Solution 4)
   - If issues persist, implement pending operation tracking
   - More invasive but architecturally cleaner

## Testing Plan

### Unit Tests
- Test cooldown logic with mock timestamps
- Test multi-strike counter increment/reset
- Test config value parsing

### Integration Tests
1. **Rapid equipment changes**: Swap armor pieces quickly during validation interval
2. **Concurrent players**: Multiple players changing equipment simultaneously
3. **Server lag simulation**: Delay async pool to exaggerate race condition
4. **Edge cases**: Player quits during validation, dimension changes, death/respawn

### Acceptance Criteria
- No "orphaned effects" warnings during normal equipment changes
- No visible effect flickering during gameplay
- Validation still catches actual orphaned effects (test by manually adding potion effects)
- Configurable intervals work as expected

## Migration Notes

### Backwards Compatibility
- All changes are backwards compatible
- New config options have sensible defaults
- Existing configs continue to work

### Rollback Plan
If issues arise:
1. Set `meta.validation_enabled: false` in config
2. OR: Revert to commit before changes
3. Effects still work normally, just no orphan detection

## Related Issues

- **Commit 9ae9b64**: Introduced validation task and tracker system (2025-12-20)
- **Commit 48e8336**: Fixed offhand and dimension change issues (2026-01-01)
- **Known Issue**: `clearActivePotionEffects()` clears all potions (addressed by tracker)

## References

### Code Locations
- Validation task: `PotionArmorPlugin.java:102-124`
- Validation logic: `EffectManager.java:456-497`
- Effect tracking: `PlayerEffectTracker.java`
- Async effect application: `EffectManager.java:231-274`, `200-225`

### Documentation
- Architecture overview: `CLAUDE.md`
- Threading model: `CLAUDE.md` - "Threading Model" section

## Conclusion

The periodic validation task is triggering false positives due to race conditions with async effect application. The recommended fix combines equipment change cooldown, multi-strike detection, and increased validation interval. This provides defense-in-depth while maintaining the benefits of orphan detection with minimal risk.

**Next Steps**:
1. Review and approve this analysis
2. Implement Phase 1 changes
3. Test on development server
4. Deploy to production
5. Monitor for recurrence

---

**Document Version**: 1.0
**Last Updated**: 2026-01-03
**Author**: Claude Code Analysis
