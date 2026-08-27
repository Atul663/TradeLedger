-- ============================================================================
-- ONE-OFF MIGRATION: broker_credential_fields - the descriptor catalog a
-- credential form renders from.
--
-- fixed_parameters tells a strategy form what each knob is called, what type it
-- takes and what to pre-fill. Nothing did that for the OTHER form on the
-- platform: brokers.auth_type groups five brokers into three flows, which is too
-- coarse to draw an input from. Zerodha and Upstox are both oauth_redirect and
-- still want different labels; Angel One wants an MPIN and a TOTP seed. Without
-- this the UI hard-codes one layout per broker and a new broker is a release.
--
-- One row per (broker, field). No user data, safe to re-run: the CREATE is
-- guarded and the seed keys on brokers.code with ON CONFLICT DO NOTHING.
--
-- ddl-auto=update creates the table from BrokerCredentialField on the next boot
-- but creates neither the CHECK constraints, the partial index, the trigger nor
-- the rows. Run this instead of relying on that.
--
--   psql "$DB_URL" -f src/main/resources/db/broker-credential-fields-migration.sql
--
-- It also adds the GROWW row to `brokers`, which the catalog did not carry.
-- ============================================================================

DO $$
BEGIN
  IF to_regclass('public.brokers') IS NULL THEN
    RAISE EXCEPTION 'brokers does not exist - run control-plane-schema.sql first';
  END IF;
END $$;

-- Groww was missing from the catalog. auth_type 'totp' is the closest of the
-- three it allows, since a token can be minted programmatically from a TOTP.
INSERT INTO brokers (code, name, description, api_base_url, auth_type) VALUES
  ('GROWW',   'Groww',     'Groww trading APIs',   'https://api.groww.in',       'totp')
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- BROKER CREDENTIAL FIELDS - what a credential form renders, per broker.
--
-- The same idea as fixed_parameters, for the other form on the platform. A
-- DESCRIPTOR CATALOG, NOT A VALUE STORE: the value of every field described here
-- is a column on broker_credentials, encrypted where it needs to be. Nothing
-- joins to this table, no user row hangs off it, and emptying it changes nothing
-- except that a form has no labels.
--
--   field_key  ->  the broker_credentials COLUMN the input binds to
--   label      ->  what the input is called on screen
--   data_type  ->  which input to render, and whether to mask it
--
-- ONE ROW PER (BROKER, FIELD). Zerodha wants an api_key, an api_secret and a
-- redirect URL; Dhan wants a client id and a pasted token; Angel One wants five
-- things, one of which it calls an MPIN. That is five different forms, and
-- without this table it is five layouts hard-coded in the UI - which is exactly
-- the shape strategy_param_definitions exists to avoid for strategy knobs. A new
-- broker is an INSERT here, never a UI release.
--
-- brokers.auth_type stays: it is the coarse grouping that says WHICH FLOW to
-- run. This says which boxes that flow needs filled, which is finer than the
-- three auth types can express - ZERODHA and UPSTOX share an auth_type and do
-- not share a form.
--
-- is_user_supplied = false marks a field the FLOW fills, not the user: Kite's
-- access_token arrives on the OAuth redirect, Angel One's jwt comes back from
-- generateSession. Those rows still exist so a form can show "connected until
-- 06:00" instead of an input nobody should type into.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS broker_credential_fields (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  broker_id        uuid NOT NULL REFERENCES brokers(id) ON DELETE CASCADE,
  field_key        varchar(50) NOT NULL,   -- the broker_credentials column
  label            varchar(100) NOT NULL,  -- 'API Secret', 'MPIN', 'Client Code'
  description      text,                   -- the help line under the input
  placeholder      varchar(200),           -- a sample value, never a real one
  data_type        varchar(20) NOT NULL DEFAULT 'text'
                   CHECK (data_type IN ('text','secret','url')),
  default_value    text,
  validation       jsonb,                  -- {"maxLength":100} / {"pattern":"..."}
  field_group      varchar(50) NOT NULL DEFAULT 'credentials'
                   CHECK (field_group IN ('credentials','session')),
  display_order    int NOT NULL DEFAULT 0,
  is_required      boolean NOT NULL DEFAULT true,
  is_user_supplied boolean NOT NULL DEFAULT true,
  help_url         text,                   -- where the user goes to get this one
  is_active        boolean NOT NULL DEFAULT true,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uq_broker_cred_fields UNIQUE (broker_id, field_key),
  CONSTRAINT ck_broker_cred_fields_order CHECK (display_order >= 0),
  -- field_key must name a real broker_credentials column. A typo'd 'apikey'
  -- would render an input that binds to nothing and fails at save time, which is
  -- the one failure a form descriptor must not be able to cause.
  CONSTRAINT ck_broker_cred_fields_key CHECK (field_key IN (
    'api_key','api_secret','access_token','refresh_token','totp_secret',
    'redirect_url','client_id','vault_ref'))
);
-- The read a form makes: one broker's fields, in the order they are shown.
CREATE INDEX IF NOT EXISTS idx_broker_cred_fields_form
  ON broker_credential_fields (broker_id, display_order, field_key)
  WHERE is_active;
DROP TRIGGER IF EXISTS trg_broker_cred_fields_touch ON broker_credential_fields;
CREATE TRIGGER trg_broker_cred_fields_touch BEFORE UPDATE ON broker_credential_fields
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- The catalog itself, keyed on brokers.code so a re-run converges instead of
-- duplicating. Order within a broker is the order the form shows.
INSERT INTO broker_credential_fields (
    broker_id, field_key, label, description, placeholder, data_type, validation,
    field_group, display_order, is_required, is_user_supplied, help_url)
SELECT b.id, v.field_key, v.label, v.description, v.placeholder, v.data_type,
       v.validation::jsonb, v.field_group, v.display_order, v.is_required,
       v.is_user_supplied, v.help_url
FROM (VALUES
  -- ZERODHA (Kite Connect): key + secret + registered redirect, exchanged daily
  ('ZERODHA','api_key','API Key',
   'From your Kite Connect app. The app is billed monthly, and historical data is billed on top of it.',
   'abcd1234efgh5678','text',NULL::text,'credentials',1,true,true,'https://developers.kite.trade'),
  ('ZERODHA','api_secret','API Secret',
   'Shown once when the app is created. It signs the SHA-256 checksum that turns a request token into an access token.',
   NULL,'secret',NULL,'credentials',2,true,true,'https://developers.kite.trade'),
  ('ZERODHA','redirect_url','Redirect URL',
   'Must match the redirect URL registered on the Kite app exactly, character for character.',
   'https://your-app.example.com/broker/zerodha/callback','url',NULL,'credentials',3,true,true,'https://developers.kite.trade'),
  ('ZERODHA','access_token','Access Token',
   'Filled in by the Kite login, not typed. Expires around 06:00 IST the next morning, which means a login every trading day.',
   NULL,'secret',NULL,'session',4,false,false,'https://kite.trade/docs/connect/v3/'),

  -- UPSTOX: plain OAuth2, same three inputs, different names and expiry
  ('UPSTOX','api_key','API Key',
   'Shown as the client ID on your Upstox app.',
   NULL,'text',NULL,'credentials',1,true,true,'https://account.upstox.com/developer/apps'),
  ('UPSTOX','api_secret','API Secret',
   'Issued with the client ID and used to exchange the authorization code for a token.',
   NULL,'secret',NULL,'credentials',2,true,true,'https://account.upstox.com/developer/apps'),
  ('UPSTOX','redirect_url','Redirect URL',
   'Must match the redirect_uri registered on the app.',
   'https://your-app.example.com/broker/upstox/callback','url',NULL,'credentials',3,true,true,'https://account.upstox.com/developer/apps'),
  ('UPSTOX','access_token','Access Token',
   'Filled in by the OAuth2 exchange, not typed. Expires around 03:30 IST the next morning.',
   NULL,'secret',NULL,'session',4,false,false,'https://upstox.com/developer/api-documentation/'),

  -- DHAN: no browser step at all - a token generated in the app and pasted
  ('DHAN','client_id','Dhan Client ID',
   'Your Dhan client id, shown beside the token in the DhanHQ section of the web or mobile app.',
   '1100123456','text','{"maxLength":100}','credentials',1,true,true,'https://web.dhan.co'),
  ('DHAN','access_token','Access Token',
   'Generate it in the DhanHQ section and paste it here. It stays valid about 30 days, so this is a monthly chore, not a daily one.',
   NULL,'secret',NULL,'credentials',2,true,true,'https://dhanhq.co/docs/v2/'),

  -- ANGELONE (SmartAPI): the odd one - an MPIN and a TOTP seed, no API secret
  ('ANGELONE','api_key','API Key',
   'The SmartAPI key for the app type you registered: trading, market data or publisher.',
   NULL,'text',NULL,'credentials',1,true,true,'https://smartapi.angelone.in'),
  ('ANGELONE','client_id','Client Code',
   'The client code you log in to Angel One with.',
   'A123456','text','{"maxLength":100}','credentials',2,true,true,'https://smartapi.angelone.in'),
  ('ANGELONE','api_secret','MPIN',
   'The MPIN you log in with. It is stored in api_secret because Angel One issues no API secret of its own.',
   NULL,'secret','{"minLength":4,"maxLength":6}','credentials',3,true,true,'https://smartapi.angelone.in'),
  ('ANGELONE','totp_secret','TOTP Secret',
   'The base32 seed shown when TOTP is enabled on the account - the seed itself, not the six digits it generates.',
   NULL,'secret','{"minLength":16}','credentials',4,true,true,'https://smartapi.angelone.in/docs'),
  ('ANGELONE','access_token','Access Token (JWT)',
   'Returned by generateSession and stored for you. Lasts about 24h and is renewed with the refresh token, so there is no daily login.',
   NULL,'secret',NULL,'session',5,false,false,'https://smartapi.angelone.in/docs'),
  ('ANGELONE','refresh_token','Refresh Token',
   'Returned alongside the JWT and used to renew it without a fresh login.',
   NULL,'secret',NULL,'session',6,false,false,'https://smartapi.angelone.in/docs'),

  -- GROWW: either paste a token, or hand over a key and let the platform mint one
  ('GROWW','access_token','Access Token',
   'Generate it in the Trading APIs section of your Groww profile and paste it here. It expires early each morning.',
   NULL,'secret',NULL,'credentials',1,true,true,'https://groww.in/user/profile/trading-apis'),
  ('GROWW','api_key','API Key',
   'Only needed if you want the token generated programmatically instead of pasted in daily.',
   NULL,'text',NULL,'credentials',2,false,true,'https://groww.in/user/profile/trading-apis'),
  ('GROWW','api_secret','API Secret',
   'Pairs with the API key when the token is generated programmatically.',
   NULL,'secret',NULL,'credentials',3,false,true,'https://groww.in/user/profile/trading-apis'),
  ('GROWW','totp_secret','TOTP Secret',
   'The base32 seed, used when a token is generated programmatically rather than pasted.',
   NULL,'secret','{"minLength":16}','credentials',4,false,true,'https://groww.in/trade-api/docs'),

  -- DELTA: signed requests, so the key is the session and nothing expires nightly
  ('DELTA','api_key','API Key',
   'Created under API keys in your Delta account settings.',
   NULL,'text',NULL,'credentials',1,true,true,'https://india.delta.exchange'),
  ('DELTA','api_secret','API Secret',
   'Shown once at creation. It signs every request with HMAC-SHA256, so there is no login step and nothing to refresh.',
   NULL,'secret',NULL,'credentials',2,true,true,'https://docs.delta.exchange/')
) AS v(code, field_key, label, description, placeholder, data_type, validation,
       field_group, display_order, is_required, is_user_supplied, help_url)
JOIN brokers b ON b.code = v.code
ON CONFLICT (broker_id, field_key) DO NOTHING;

-- Verify:
--   SELECT b.code, f.display_order, f.field_key, f.label, f.data_type,
--          f.is_required, f.is_user_supplied
--   FROM broker_credential_fields f JOIN brokers b ON b.id = f.broker_id
--   ORDER BY b.code, f.display_order;
--   -- expect 22 rows: 4 ZERODHA, 4 UPSTOX, 2 DHAN, 6 ANGELONE, 4 GROWW, 2 DELTA
--
--   SELECT b.code FROM brokers b
--   LEFT JOIN broker_credential_fields f ON f.broker_id = b.id
--   WHERE f.id IS NULL;
--   -- expect none: a broker with no fields is a broker whose form is blank.
