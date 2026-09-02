-- ============================================================================
-- Migration 006: Profile photos
--
-- Stored as a BLOB in the database, deliberately -- not as a file path.
-- BloodLink is a shared-MySQL, multi-machine app (different users' desktop
-- clients all pointing at one database), so a file path would only ever
-- resolve on whichever machine uploaded it. A BLOB travels with the row.
--
-- On users, not donor_profiles: a photo is an identity concept applicable
-- to any role, not a donor-specific health attribute, even though only the
-- donor and requester dashboards currently expose an upload UI for it.
--
-- Application-layer enforced, not a DB constraint: max 2MB per photo
-- (see ProfileService.MAX_PHOTO_BYTES). MEDIUMBLOB's real ceiling is 16MB;
-- the app keeps it far below that on purpose.
--
-- Never selected by the routine user/donor list queries used for matching,
-- search, or dashboards -- only a dedicated UserDAO.findPhoto(userId) call,
-- made once when actually displaying one specific profile, ever reads this
-- column. Loading it as part of every donor row during matching would
-- silently move megabytes of image data through every matching pass.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN photo MEDIUMBLOB NULL AFTER address;
