package com.bloodlink.model;

/**
 * PARTIALLY_FULFILLED is new: added as schema/model groundwork for multi-donor
 * requests (units_needed > 1, satisfied by more than one donor's confirmed
 * donation) ahead of the DAO/service/controller logic that will actually
 * produce it. No code path sets this status yet -- see
 * migration_005_multi_donor_groundwork.sql and INSTRUCTIONS.md for the
 * planned state machine.
 */
public enum RequestStatus {
    PENDING, MATCHED, ACCEPTED, PARTIALLY_FULFILLED, DECLINED, FULFILLED, CANCELLED, ESCALATED
}
