package ru.ildar.safebox.application.pricing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final Counter hits;
    private final Counter misses;

    public PricingService(StringRedisTemplate redis, MeterRegistry registry) {
        this.redis = redis;
        this.hits = registry.counter("pricing.cache.hit");
        this.misses = registry.counter("pricing.cache.miss");
    }

    public BigDecimal calculate(String cellType, int days, String segment) {
        String key = "price:" + cellType + ":" + days + ":" + segment;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                hits.increment();
                return new BigDecimal(cached);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, fallback to compute: {}", e.getMessage());
        }
        misses.increment();
        BigDecimal price = compute(cellType, days, segment);
        try {
            redis.opsForValue().set(key, price.toPlainString(), TTL);
        } catch (Exception e) {
            log.warn("Redis set failed: {}", e.getMessage());
        }
        return price;
    }

    private BigDecimal compute(String cellType, int days, String segment) {
        BigDecimal base = switch (cellType) {
            case "SMALL" -> new BigDecimal("100");
            case "MEDIUM" -> new BigDecimal("200");
            case "LARGE" -> new BigDecimal("400");
            default -> new BigDecimal("150");
        };
        BigDecimal discount = "VIP".equals(segment) ? new BigDecimal("0.8") : BigDecimal.ONE;
        return base.multiply(BigDecimal.valueOf(days)).multiply(discount);
    }
}
