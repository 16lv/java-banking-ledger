package agency.shss7.clearledger.model;

import java.math.BigInteger;

/**
 * Strategy interface for calculating per-withdrawal fees.
 */
@FunctionalInterface
public interface FeePolicy {
    long calculateFeePence(long withdrawalAmountPence);

    static FeePolicy zeroFee() {
        return amount -> 0L;
    }

    static FeePolicy flatFee(long flatFeePence) {
        if (flatFeePence < 0) {
            throw new IllegalArgumentException("Flat fee cannot be negative.");
        }
        return amount -> flatFeePence;
    }

    /**
     * Charges a basis-point fee (100 bps = 1.00%) only on the portion of a withdrawal
     * above freeThresholdPence, avoiding signed 64-bit multiplication overflow.
     */
    static FeePolicy tieredFee(long freeThresholdPence, int rateBasisPoints) {
        if (freeThresholdPence < 0 || rateBasisPoints < 0) {
            throw new IllegalArgumentException("Threshold and basis points cannot be negative.");
        }
        return amount -> {
            if (amount <= freeThresholdPence) {
                return 0L;
            }
            long taxablePence = amount - freeThresholdPence;
            return BigInteger.valueOf(taxablePence)
                .multiply(BigInteger.valueOf(rateBasisPoints))
                .divide(BigInteger.valueOf(10_000L))
                .longValueExact();
        };
    }
}
