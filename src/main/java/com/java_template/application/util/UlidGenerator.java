package com.java_template.application.util;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Utility class for generating short ULID-style identifiers
 * Generates compact, time-ordered identifiers suitable for order numbers
 */
public class UlidGenerator {
    
    private static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    
    /**
     * Generate a short ULID (8 characters) for order numbers
     * Format: 4 chars timestamp + 4 chars random
     */
    public static String generateShortUlid() {
        long timestamp = Instant.now().toEpochMilli();
        
        // Use last 20 bits of timestamp (about 12 days range)
        long timestampPart = timestamp & 0xFFFFF;
        
        // Generate 4 characters from timestamp (20 bits = 4 * 5 bits)
        StringBuilder result = new StringBuilder(8);
        for (int i = 0; i < 4; i++) {
            int index = (int) ((timestampPart >> (15 - i * 5)) & 0x1F);
            result.append(CROCKFORD_BASE32.charAt(index));
        }
        
        // Generate 4 random characters
        for (int i = 0; i < 4; i++) {
            int index = RANDOM.nextInt(32);
            result.append(CROCKFORD_BASE32.charAt(index));
        }
        
        return result.toString();
    }
}
