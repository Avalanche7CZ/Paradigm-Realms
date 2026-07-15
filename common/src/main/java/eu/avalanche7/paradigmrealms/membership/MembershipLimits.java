package eu.avalanche7.paradigmrealms.membership;

import java.time.Duration;
import java.util.Objects;

public record MembershipLimits(Duration invitationExpiry, int maximumMembers, int maximumPendingInvitations) {
    public MembershipLimits {
        Objects.requireNonNull(invitationExpiry, "invitationExpiry");
        if (invitationExpiry.isZero() || invitationExpiry.isNegative() || invitationExpiry.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("invitation expiry must be between one millisecond and 30 days");
        }
        if (maximumMembers < 1 || maximumMembers > 1_000) {
            throw new IllegalArgumentException("maximumMembers must be between 1 and 1000");
        }
        if (maximumPendingInvitations < 1 || maximumPendingInvitations > 1_000) {
            throw new IllegalArgumentException("maximumPendingInvitations must be between 1 and 1000");
        }
    }
}
