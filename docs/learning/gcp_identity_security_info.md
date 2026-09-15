# GCP Identity Platform and Spring Security

## 1. What this milestone teaches

Identity Platform authenticates a user and issues a signed ID token. Spring Security verifies that token before the controller runs. Application-issued permissions then decide whether the caller can search or publish. PostgreSQL filters search results by the trusted tenant and read groups.

The application never uses your email as authorization, stores your password, or grants permissions from HTTP request fields. A UID identifies an account; it is not a credential. GCP IAM permissions for administrators and application permissions for end users are separate systems.

Implemented: authentication, scoped publisher authorization, authorized keyword search, and transactional ingestion. Not implemented: hosted login UI, live revocation checks, Cloud Run deployment, embeddings, generation or agent execution.

## 2. The permission contract

An administrator sets this custom-claim namespace for a support publisher:

```json
{
  "akh": {
    "tenant": "local",
    "groups": ["support"],
    "permissions": ["knowledge:read", "knowledge:write"],
    "write_sources": ["manual-text"],
    "assignable_groups": ["support"]
  }
}
```

`groups` controls reading. `permissions` controls API operations. `write_sources` grants source-wide publishing rights in the claimed tenant. `assignable_groups` bounds document ACL assignment. This publisher may replace any document in that source, even if its existing ACL prevents the publisher reading it. This is a deliberate publishing role, not document ownership. Use a narrower policy before supporting independently owned documents.

`local` is an application tenant identifier stored in PostgreSQL; it is not an Identity Platform multi-tenancy resource. This setup uses the project's default Identity Platform user pool. Identity Platform tenants would need a separately designed and validated tenant mapping.

## 3. Assign permissions through Cloud Shell

Open Cloud Shell in the project. You need administrator permissions `firebaseauth.users.get` and `firebaseauth.users.update`. The runtime Java application does not need those permissions or a service-account key.

Upload [set_identity_claims.py](../../scripts/set_identity_claims.py) with Cloud Shell's file upload menu, or copy its contents into a file of the same name. From the directory containing it, preview the assignment:

```bash
python3 set_identity_claims.py \
  --project agentic-knowledge-hub-learning \
  --uid YOUR_IDENTITY_PLATFORM_UID \
  --access publisher
```

Add `--apply` to apply it and verify the saved claims. Defaults are tenant `local`, group `support`, source `manual-text`. `--access reader` removes publishing capability from this application's namespace. The helper preserves unrelated namespaces, replaces the `akh` namespace, checks the claim-size limit and reads back the result. It never prints credentials or user records. Run one administrator update at a time: the read/modify/write sequence cannot protect against a concurrent administrator replacing claims.

The Python file is only an administration utility for Cloud Shell's built-in tools; the application remains Java. It uses Google's [account update API](https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/projects.accounts/update) and [account lookup API](https://docs.cloud.google.com/identity-platform/docs/reference/rest/v1/projects.accounts/lookup).

After a claim change, sign in again or force token refresh. Existing tokens contain the old claims; changing an account does not rewrite an already-issued token. See [custom claims](https://firebase.google.com/docs/auth/admin/custom-claims).

## 4. Sign in privately on your Mac

Enable Email/Password in Identity Platform and create the learning account. Obtain the project's Web API key from **Application setup details**. An API key identifies the project for sign-in; it is not the user's ID token and grants no publisher rights by itself. Respect any configured key restrictions.

Run the following in your own terminal, not in a shared transcript. It prompts for the API key, email and password without putting them into command history. It copies the resulting ID token to the macOS clipboard without printing it. Do not send the token or password to an assistant, commit it, or paste it into a third-party JWT inspection site.

```bash
python3 - <<'PY'
import getpass, json, subprocess, urllib.parse, urllib.request
try:
    api_key = getpass.getpass('Identity Platform Web API key: ')
    email = getpass.getpass('Learning user email (hidden): ')
    password = getpass.getpass('Password: ')
    url = 'https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=' + urllib.parse.quote(api_key, safe='')
    req = urllib.request.Request(url, data=json.dumps({
        'email': email, 'password': password, 'returnSecureToken': True
    }).encode(), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=30) as response:
        result = json.load(response)
    subprocess.run(['pbcopy'], input=result['idToken'], text=True, check=True)
    print('ID token copied. Paste it into Swagger Authorize, then clear the clipboard.')
except Exception:
    print('Sign-in failed. Check provider, credentials, API key restrictions and network access.')
PY
```

This simple learning flow does not handle MFA or reCAPTCHA challenges. Use a supported client SDK/login UI if your project requires them. Google's [email/password sign-in reference](https://firebase.google.com/docs/reference/rest/auth#section-sign-in-email-password) describes the exchange.

## 5. Run and exercise the API

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=dev GCP_PROJECT_ID=agentic-knowledge-hub-learning ./mvnw spring-boot:run
```

Restart any previously running Java process to load the new code. Open [Swagger](http://localhost:8000/docs), click **Authorize**, and paste only the ID token (Swagger adds `Bearer`). Authorization is not persisted across browser reloads. Clear the clipboard with `printf '' | pbcopy` after pasting. Sign in again when the token expires.

Ingest:

```json
{
  "external_id": "identity-learning",
  "title": "Cloud Run Guide",
  "text": "Cloud Run hosts the deployment API.",
  "allowed_groups": ["support"]
}
```

Expect CHANGED, then UNCHANGED for an identical retry. Edit the text and expect CHANGED with replaced chunks. Search with `{"query":"Cloud Run","limit":5}` and inspect persisted citations. Assigning `admin` instead of `support` must return 403. Removing authorization must return 401. Existing synthetic documents need support ACLs to appear in support searches.

For curl examples elsewhere in this handbook, set `AKH_ID_TOKEN` in your own terminal from your private token, then use `Authorization: Bearer $AKH_ID_TOKEN`. Avoid sharing shell traces, process listings or screenshots containing the expanded token; Swagger is the simpler learning path. Clear the shell variable when finished with `unset AKH_ID_TOKEN`.

## 6. Validation and failure behavior

The decoder accepts RS256 signatures from Google's fixed SecureToken JWK endpoint. It validates the configured issuer and project audience, expiry, issued-at, authentication time, a nonempty subject (at most 128 characters), and a key ID. It allows 60 seconds of clock skew. It does not accept generic `gcloud auth print-identity-token` tokens, OAuth access tokens, or Admin SDK custom tokens. See Google's [ID-token verification rules](https://firebase.google.com/docs/auth/admin/verify-id-tokens).

| Result | Meaning |
| --- | --- |
| 401 | Missing, invalid, expired, incorrectly signed or wrong-project ID token |
| 403 | Valid identity lacks operation/source/ACL permission, or application claims are absent/malformed |
| 200 with empty results | Valid reader has no eligible matching evidence |
| 422 | Authorized request has invalid input |
| 503 | Authorized operation cannot reach persistence/search |

The health endpoint is public. Swagger is public only under `dev`; API authentication is still mandatory in that profile. Other routes are denied. Signature verification may need network access when Google's keys are not cached. Unverifiable tokens fail closed.

Local JWT verification does not detect account disablement or token revocation immediately. Already-issued tokens can work until expiration plus allowed clock skew. Immediate revocation needs an additional policy/revocation lookup, with a cache and outage policy. Document ACLs, however, are checked in the database on each search statement.

## 7. Spring, transactions and architect interview preparation

Read [SecurityConfiguration](../../src/main/java/com/agenticknowledgehub/config/SecurityConfiguration.java), [IdentityTokenValidator](../../src/main/java/com/agenticknowledgehub/security/IdentityTokenValidator.java), [IdentityClaims](../../src/main/java/com/agenticknowledgehub/security/IdentityClaims.java), then [AuthorizedIngestionService](../../src/main/java/com/agenticknowledgehub/security/AuthorizedIngestionService.java).

- **Filter chain:** Authentication and endpoint authorization occur before transport/controller code. A decoded token is not a verified token.
- **Dependency inversion:** Retrieval consumes a `CallerContextProvider`; it does not parse JWTs. The security adapter supplies verified identity.
- **Service facade:** Authorized ingestion checks publisher policy before invoking parsing and persistence. Controller code remains concerned with HTTP mapping.
- **Single responsibility:** Token validation, claim mapping, publishing policy, retrieval and transaction coordination have separate reasons to change.
- **Transaction boundary:** Parsing stays outside the transaction. Trusted tenant/source determine deterministic IDs and the existing source lock. The database transaction rechecks durable state and atomically replaces chunks. Authentication does not replace idempotency or concurrency control.
- **TOCTOU:** We explicitly grant source-wide publishing; we do not perform a racy read of per-document ownership before writing. A future document-ownership policy must be checked under the write transaction's lock.
- **Statelessness:** Bearer headers authenticate each request. No session cookies are used, so CSRF is disabled. Reassess if browser cookie authentication is added.
- **Agent safety:** Retrieved documents cannot grant tool permissions or widen tenant scope. Later agent tools must execute under application-controlled permissions.

Interview questions: Why check audience as well as signature? Why is `dev` not an authentication mechanism? How do claim changes differ from ACL changes? What does a source-wide publisher grant allow? How would immediate revocation affect availability? How would Cloud Run IAM and end-user identity coexist?

## 8. Verification

```bash
./mvnw spotless:apply
./mvnw verify
git diff --check
```

Tests generate RSA keys and sign synthetic tokens. Real HTTP tests reject forged/expired/wrong-project tokens and denied writes, and use disposable PostgreSQL to verify tenant isolation, retries and replacement. Existing rollback and concurrency tests remain active. These tests need Docker but no GCP credentials; live sign-in and Google key retrieval are separate manual checks.

Return to the [learning index](README.md).
