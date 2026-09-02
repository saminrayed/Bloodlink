package com.bloodlink.model;

import java.util.EnumSet;
import java.util.Set;

public enum BloodGroup {
    O_NEGATIVE("O−"), O_POSITIVE("O+"), A_NEGATIVE("A−"), A_POSITIVE("A+"),
    B_NEGATIVE("B−"), B_POSITIVE("B+"), AB_NEGATIVE("AB−"), AB_POSITIVE("AB+");

    private final String displayName;
    BloodGroup(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
    @Override public String toString() { return displayName; }

    public boolean canDonateTo(BloodGroup recipient) {
        return compatibleRecipients().contains(recipient);
    }

    public Set<BloodGroup> compatibleRecipients() {
        return switch (this) {
            case O_NEGATIVE -> EnumSet.allOf(BloodGroup.class);
            case O_POSITIVE -> EnumSet.of(O_POSITIVE, A_POSITIVE, B_POSITIVE, AB_POSITIVE);
            case A_NEGATIVE -> EnumSet.of(A_NEGATIVE, A_POSITIVE, AB_NEGATIVE, AB_POSITIVE);
            case A_POSITIVE -> EnumSet.of(A_POSITIVE, AB_POSITIVE);
            case B_NEGATIVE -> EnumSet.of(B_NEGATIVE, B_POSITIVE, AB_NEGATIVE, AB_POSITIVE);
            case B_POSITIVE -> EnumSet.of(B_POSITIVE, AB_POSITIVE);
            case AB_NEGATIVE -> EnumSet.of(AB_NEGATIVE, AB_POSITIVE);
            case AB_POSITIVE -> EnumSet.of(AB_POSITIVE);
        };
    }
}
