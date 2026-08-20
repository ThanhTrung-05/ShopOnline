-- New registrations delegate authentication and password storage exclusively to Keycloak.
-- Existing hashes are retained as legacy data; this migration performs no destructive cleanup.

ALTER TABLE CUSTOMERS MODIFY (PASSWORD_HASH NULL);

COMMENT ON COLUMN CUSTOMERS.PASSWORD_HASH IS
    'Legacy password hash; NULL for Keycloak-managed customer registrations';

COMMIT;
