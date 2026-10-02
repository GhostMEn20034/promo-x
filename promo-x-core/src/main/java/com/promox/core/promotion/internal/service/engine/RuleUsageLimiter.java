package com.promox.core.promotion.internal.service.engine;

/**
 * Gate the engine asks before applying a usage-limited rule. Keeps the engine free of
 * storage concerns, so it can be exercised without Redis.
 */
public interface RuleUsageLimiter {

    /**
     * Takes one use of the rule.
     *
     * @return false when the rule has no uses left
     */
    boolean tryAcquire(Long ruleId);

    /**
     * Gives back a use taken by {@link #tryAcquire(Long)}.
     */
    void release(Long ruleId);
}
