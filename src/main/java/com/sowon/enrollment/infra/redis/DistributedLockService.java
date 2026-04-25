package com.sowon.enrollment.infra.redis;

import com.sowon.enrollment.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

    private static final long LOCK_TTL_SECONDS  = 3L;
    private static final long WAIT_TIMEOUT_MS   = 3_000L;
    private static final long RETRY_INTERVAL_MS = 100L;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end",
            Long.class
    );

    /**
     * 락 획득 시도. 최대 WAIT_TIMEOUT_MS 동안 RETRY_INTERVAL_MS 간격으로 재시도.
     * @return 락 식별용 고유 값 (unlock 시 사용)
     * @throws BusinessException 락 획득 실패 시
     */
    public String tryLock(String lockKey) {
        String lockValue = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(acquired)) {
                return lockValue;
            }
            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("LOCK_INTERRUPTED", "Lock acquisition was interrupted");
            }
        }
        throw new BusinessException("LOCK_ACQUISITION_FAILED",
                "Failed to acquire distributed lock: " + lockKey);
    }

    /**
     * 락 해제. lockValue가 일치할 때만 삭제하여 타 스레드 락 침범 방지.
     */
    public void unlock(String lockKey, String lockValue) {
        redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), lockValue);
    }
}
