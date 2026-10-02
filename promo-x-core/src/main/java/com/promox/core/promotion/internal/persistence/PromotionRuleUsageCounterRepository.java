package com.promox.core.promotion.internal.persistence;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Remaining-uses counters of usage-limited promotion rules, one Redis key per rule.
 * The counter is the fast, atomic gate used while rules are applied; the rule's
 * {@code remaining_uses} column stays the durable value and seeds a missing counter.
 */
@Repository
public class PromotionRuleUsageCounterRepository {
    private static final String KEY_PREFIX = "promotion-rule:remaining-uses:";

    private static final long USE_TAKEN = 1;
    private static final long NO_USES_LEFT = 0;

    /**
     * Decides whether the rule may be applied and takes the use in the same atomic step, so two
     * requests can never both take the last one. Returns:
     * <ul>
     *   <li>{@code 1}: a use was left; the counter was decreased and the rule may be applied</li>
     *   <li>{@code 0}: the counter is at 0; nothing was changed and the rule must be rejected</li>
     *   <li>{@code -1}: the rule has no counter yet</li>
     * </ul>
     */
    private static final RedisScript<Long> TAKE_USE_IF_AVAILABLE = RedisScript.of("""
        local remaining = redis.call('GET', KEYS[1])
        if not remaining then return -1 end
        if tonumber(remaining) <= 0 then return 0 end
        redis.call('DECR', KEYS[1])
        return 1
        """, Long.class);

    // A plain INCR would create a missing key with value 1 and then block seeding from the database.
    private static final RedisScript<Long> INCREMENT_IF_PRESENT = RedisScript.of("""
        if redis.call('EXISTS', KEYS[1]) == 1 then return redis.call('INCR', KEYS[1]) end
        return -2
        """, Long.class);

    public enum Acquisition {
        /** A use was taken: the counter was decreased and the rule may be applied. */
        ACQUIRED,
        /** The counter is at 0: nothing was changed and the rule must not be applied. */
        EXHAUSTED,
        /** No counter exists for the rule yet; it has to be initialized first. */
        MISSING
    }

    private final StringRedisTemplate redisTemplate;

    public PromotionRuleUsageCounterRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Takes one use of the rule if any is left. {@link Acquisition#ACQUIRED} is the only result
     * for which the counter was decreased.
     */
    public Acquisition tryDecrement(Long ruleId) {
        Long result = redisTemplate.execute(TAKE_USE_IF_AVAILABLE, List.of(key(ruleId)));

        if (result == null) {
            return Acquisition.MISSING;
        }
        if (result == USE_TAKEN) {
            return Acquisition.ACQUIRED;
        }
        return result == NO_USES_LEFT ? Acquisition.EXHAUSTED : Acquisition.MISSING;
    }

    /**
     * Gives one use back. Does nothing when the rule has no counter; the next seeding from the
     * database will already reflect the restored value.
     */
    public void increment(Long ruleId) {
        redisTemplate.execute(INCREMENT_IF_PRESENT, List.of(key(ruleId)));
    }

    /**
     * Creates the counter unless a concurrent request already did.
     */
    public void initializeIfAbsent(Long ruleId, int remainingUses) {
        redisTemplate.opsForValue().setIfAbsent(key(ruleId), String.valueOf(Math.max(remainingUses, 0)));
    }

    public void delete(Long ruleId) {
        redisTemplate.delete(key(ruleId));
    }

    private static String key(Long ruleId) {
        return KEY_PREFIX + ruleId;
    }
}
