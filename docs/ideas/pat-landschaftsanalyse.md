# Analyse: Personal Access Tokens (PAT) — was es im Java-/Spring-Umfeld bereits gibt

> **Status:** Analyse **mit Entscheidung** — siehe Abschnitt „Entscheidung vom 2026-09-10" am Ende.
> PAT ist zurückgestellt; der Weg ist Token Exchange (RFC 8693).
> **Stand:** 2026-09-10
> **Kontext:** Open Elements `spring-services` 1.4.0-SNAPSHOT, Java 21, Spring Boot 3.5.14,
> Spring Security 6.5.10, IdP ist Authentik
> **Anlass:** PAT soll als nächstes dazukommen. Vor dem Design die Frage: baut man das selbst,
> oder gibt es das schon?

---

## 0. Kernbefunde in fünf Sätzen

1. **Spring Security bringt kein PAT-/API-Key-Feature mit** — nicht in 6.5, nicht in 7.0/7.1. Es
   bringt aber genau die Schnittstelle mit, an der man ein eigenes einhängt: `OpaqueTokenIntrospector`.
2. **Es gibt auch keine brauchbare Bibliothek.** Der einzige auffindbare Spring-Boot-Starter zum
   Thema ist seit Februar 2023 unverändert, steht auf Version 0.6.1 und prüft einen *statischen*
   Key in einem `HandlerInterceptor` — kein Ausgeben, kein Hashen, kein Widerrufen.
3. **Der „kaufen statt bauen"-Weg existiert zweifach**, aber für den USER-PAT-Fall passt keiner
   sauber: Spring Authorization Server (opake *reference tokens* + Introspection-Endpoint) und
   Authentik selbst (Service Accounts + App Passwords + `client_credentials`).
4. **Dieses Repo hat den Spec dafür schon** — Spec 010 (`docs/specs/010-api-token-module/`,
   Status `open`), geschrieben im Juni bei 1.1.0-SNAPSHOT, inklusive Datenmodell, Token-Format und
   Flows. Teile davon sind seither gelandet (`PrincipalDirectory`, `UserEntityPrincipalDirectory`),
   andere Annahmen sind veraltet.
5. **Die technischen Detailentscheidungen in Spec 010 stimmen mit dem Stand der Technik überein**
   (opak statt JWT, ~256 Bit Entropie, SHA-256 statt bcrypt mit derselben Begründung, die die
   Praxisliteratur nennt, Vendor-Prefix für Secret-Scanner, Live-Auflösung der Rechte) — die offene Frage ist nicht *wie*, sondern *ob selbst*.

---

## Teil A — Was in diesem Repo schon existiert

| Baustein | Ort | Zustand |
| --- | --- | --- |
| **Spec 010 „API Token Module"** | `docs/specs/010-api-token-module/design.md` (22 KB) + `behaviors.md` (9 KB) | `open`. Vollständiges Design: ein Token-Typ für `SERVICE` + `USER`, eine Tabelle `api_token` + `api_token_scope`, Format `oe_<typeCode>_<tokenId>_<secret>`, Validierung über `OpaqueTokenIntrospector`, vier Flows, Migrationspfad weg von `ApiKey` |
| **`PrincipalDirectory`** (Port) | `services/apitoken/PrincipalDirectory.java` | **Gelandet.** Javadoc sagt ausdrücklich: als Gerüst von Spec 012 vorgezogen, Spec 010 wird „permanent owner". Enthält `ResolvedPrincipal(subjectRef, active, roles, groups, displayName)` |
| **`UserEntityPrincipalDirectory`** (Default-Impl) | `services/user/UserEntityPrincipalDirectory.java` | **Gelandet** (Spec 012). Damit ist die in Spec 010 unter *Open Questions* gestellte Frage „wo landet die Default-Implementierung?" beantwortet |
| **Bestehende `ApiKey`-Infrastruktur** | `services/apikey/*` (7 Typen), `security/apikey/ApiKeyAuthenticationFilter` | In Produktion auf `/api/external/**` (`X-API-Key`) und in `spring-services-mcp`. Spec 010 will sie **ersetzen** (Breaking Change) |
| **`AuthenticationType`** | Spec 019 (`docs/specs/019-authentication-type/`), noch nicht implementiert | Klassifiziert Aufrufer als `JWT_ACCOUNT`/`API_KEY`/`ANONYMOUS`/`OTHER`/`NONE`. **Berührungspunkt:** ein PAT-Aufrufer ist weder das eine noch das andere — siehe Teil G |
| **Rollen-Zugriff** | `AuthService.getRoles()` (Spec 017, gelandet) | Liest Rollen prefixfrei aus den Authorities. Ein PAT-Principal muss seine Authorities so setzen, dass das trägt |
| **SCIM-Nutzerspiegel** | Spec 015 (gelandet) | `UserEntity` wird per SCIM von Authentik gefüttert — die Datenquelle, aus der `UserEntityPrincipalDirectory` die Live-Rechte zieht |

### Was an Spec 010 seit Juni veraltet ist

- Es nennt die Library „`1.1.0-SNAPSHOT`" als Begründung, den Breaking Change *jetzt* zu nehmen —
  wir sind bei **1.4.0-SNAPSHOT**, und es gibt seit 1.3/1.4 dokumentierte Upgrade-Pfade
  (`docs/releases/upgrade-to-1.4.md`). Das Argument „kostet noch nichts" gilt nicht mehr unverändert.
- Es geht von einem Einzelartefakt aus. Seit Spec 014/016 ist das ein Maven-Reactor; ein Token-Modul
  wäre heute eine Modulfrage (`core` oder eigenes Feature-Modul) — und `docs/TODO.md` führt bereits
  den Eintrag *„Extract an integration module for API keys, PATs & webhooks"*.
- Zwei seiner *Open Questions* sind faktisch entschieden (Default-`PrincipalDirectory` ist da).
- Die `security → services.apikey`-Kante, die ein Token-Modul erbt, steht inzwischen als Schuld in
  `docs/TODO.md` (*„Repay the two package cycles"*).

---

## Teil B — Was Spring Security selbst mitbringt

Geprüft durch Inventar der Jars 6.5.10 (die von Boot 3.5.14 gemanagte Version) und der Quellen.

### Vorhanden

| Baustein | Was es tut |
| --- | --- |
| `OpaqueTokenIntrospector` | **Die Einhängestelle.** Ein Interface `OAuth2AuthenticatedPrincipal introspect(String token)`. Spring Security ist das Tokenformat ausdrücklich gleichgültig — die Referenz sagt, die Unterstützung sei „designed to not care about the format of the token" |
| `OpaqueTokenAuthenticationProvider` | Ruft den Introspector und baut daraus eine `BearerTokenAuthentication` mit Authorities |
| `OpaqueTokenAuthenticationConverter` | Erlaubt, den erzeugten `Authentication`-Typ selbst zu bestimmen |
| `BearerTokenAuthenticationFilter` + `BearerTokenResolver` / `DefaultBearerTokenResolver` / `HeaderBearerTokenResolver` | Extrahiert das Token aus `Authorization: Bearer …` (optional Form/Query) |
| `SpringOpaqueTokenIntrospector` (+ reaktive Variante) | Fertige Implementierung für **RFC 7662** — Introspection per HTTP gegen einen *fremden* Authorization Server |
| `BearerTokenAuthenticationEntryPoint`, `BearerTokenAccessDeniedHandler`, `BearerTokenErrors` | RFC-6750-konforme 401/403-Antworten inklusive `WWW-Authenticate` |
| `PasswordEncoder`-Familie (`BCrypt`, `Argon2`, `Pbkdf2`, `Delegating`) | Für Passwörter gedacht — für hochentropische Tokens die falsche Wahl, siehe Teil F |

### Nicht vorhanden

Kein Speicher, kein Ausgeben, kein Widerrufen, kein Ablaufdatum, kein Hashen, keine Entität, keine
Verwaltungs-API — und **kein API-Key-Mechanismus überhaupt**. Das Jar-Inventar von
`spring-security-web` und `spring-security-core` 6.5.10 enthält keine einzige Klasse mit `ApiKey`
im Namen.

### Der offizielle Stand der Diskussion

[`spring-projects/spring-security#17563` „API key authentication support"](https://github.com/spring-projects/spring-security/issues/17563)
fordert genau das, was hier gebraucht wird: Keys als Hash in der DB, Rechte pro Key, sofortiger
Widerruf, befristete und unbefristete Keys, Extraktion aus dem Header, `Authentication` mit
Authorities, keine Session. Zustand beim Abruf am 2026-09-10:

- **eröffnet am 2025-07-20**, seit über einem Jahr **offen**,
- Labels `status: waiting-for-triage` und `type: enhancement`,
- **kein Milestone, kein Assignee**, keine Zusage eines Maintainers.

Auch die Release Notes von Spring Security 7.0 führen kein API-Key-Feature; die dort genannten
Neuerungen sind Multi-Factor-Authentifizierung (`AllAuthoritiesAuthorizationManager`,
`Authentication.Builder`), das Eingemeinden von Spring Authorization Server und der Kerberos-Extension,
SAML-/OpenSAML-5-Modernisierung sowie eine RFC-konformere Client-Credential-Kodierung bei der
Introspection.

> **Fazit Teil B:** Die Schnittstelle ist da, die Substanz nicht — und es gibt keinen Hinweis, dass
> sich das absehbar ändert. Wer wartet, wartet auf ein Ticket ohne Milestone.

### Versionslage, die man mitentscheiden muss

Spring Security ist inzwischen bei **7.1.1**, Spring Boot 4 ist die Grundlage dafür. Dieses Repo
steht auf **6.5.10 / Boot 3.5.14**. Alles, was aus der 7er-Linie kommt, ist hier erst nach dem
Boot-4-Sprung verfügbar.

---

## Teil C — Spring Authorization Server (SAS)

SAS kann opake Tokens ausgeben: `OAuth2TokenFormat` hat neben `SELF_CONTAINED` (JWT) den Wert
**`REFERENCE`** — geprüft im Jar 7.1.1. Dazu kommen `OAuth2TokenIntrospectionEndpointFilter`,
`OAuth2TokenIntrospection`, `OAuth2TokenGenerator`/`DelegatingOAuth2TokenGenerator` und
`OAuth2Authorization` als Persistenzmodell. Die Versionslinien: **1.5.8** für Boot 3.x, **7.x** ab
Boot 4 — der Sprung von `1.5.x` auf `7.0.0` in Gleichschritt mit Spring Security passt zur Aussage
der 7.0-Release-Notes, dass SAS nun Teil von Spring Security ist.

**Was das löst:** Ausgabe, Speicherung, Introspection und Widerruf opaker Tokens — standardkonform,
von Spring gepflegt.

**Warum es für PAT trotzdem schlecht passt:**

- SAS ist ein **Authorization Server**, kein Token-Verwaltungsfeature. Man betreibt damit ein
  zweites IdP neben Authentik — mit Client-Registry, Consent, Endpunkten, Schlüsselverwaltung.
- Ein PAT ist kein OAuth-Flow-Ergebnis. Der Nutzer klickt „Token erzeugen", benennt es und kopiert
  es; es gibt keinen Client, keinen Redirect, keinen Consent.
- Der Anwendungsfall „Rechte werden bei *jedem* Request live aufgelöst" (der Kern von Spec 010)
  widerspricht dem OAuth-Modell, in dem der Token seine Scopes trägt.
- Für dieses Repo aktuell ohnehin nur in der 1.5.x-Linie nutzbar (Boot 3.5).

---

## Teil D — Der IdP-Weg: Authentik

Authentik hat eigene Token-Konzepte, und dieser Stack benutzt Authentik ohnehin:

- **Service Accounts** — beim Anlegen erzeugt Authentik ein **App Password**. Dokumentiert für
  Skripte, CI/CD und Automatisierung.
- **API-Tokens** für `/api/v3/**` per HTTP-Bearer; Standard-Ablauf 360 Tage, konfigurierbar auch
  unbefristet, jederzeit widerrufbar durch Löschen.
- **`client_credentials`** für M2M: Identifikation über Service Accounts, Authentifizierung über
  App-Password-Tokens.
- **Introspection/Revocation** über den OAuth2-Provider, per Default nur für Tokens desselben
  Providers, optional providerübergreifend.

**Bewertung:** Für den `SERVICE`-Fall aus Spec 010 — Maschine ruft unsere API — ist das eine echte
Alternative: Authentik gibt das Token aus, wir prüfen es per Introspection oder als JWT, und die
bestehende JWT-Kette trägt fast alles schon. Für den **`USER`-PAT-Fall** passt es nicht: der Nutzer
soll sich in *unserer* Anwendung ein benanntes Token erzeugen, mit unserem Ablauf, unserer Anzeige
„zuletzt benutzt" und unserem Widerruf — nicht im Admin-Interface des IdP, zu dem er in der Regel
keinen Zugang hat.

---

## Teil E — Drittanbieter-Bibliotheken

Gesucht auf Maven Central und im Web. Ergebnis:

| Kandidat | Zustand | Bewertung |
| --- | --- | --- |
| `net.skobow:apikey-authentication-spring-boot-starter` | Letztes Release **0.6.1 vom 2023-02-25**, davor 2019. `maven-metadata.xml`: `lastUpdated 20230225` | Prüft laut eigener README einen **statischen**, konfigurierten Key im `X-Api-Key`-Header über einen `HandlerInterceptor`. Kein Ausgeben, kein Hash, kein Widerruf, keine Nutzerbindung, nicht in Spring Security integriert |
| `42BV/api-key-authentication` (GitHub) | **Nicht auf Maven Central** (404 unter `nl/_42/...`) | Als Dependency nicht konsumierbar |
| Auth0 `java-jwt`, `jjwt` | Aktiv, aber JWT-Bibliotheken | Lösen ein anderes Problem: PAT soll gerade **nicht** selbsttragend sein, sonst ist Widerruf wieder Zustand |

> **Fazit Teil E:** Es gibt nichts, was man nehmen könnte. Alle gefundenen Anleitungen (Baeldung,
> Qovery, diverse Blogs) beschreiben genau das, was Spec 010 beschreibt: man baut es selbst auf
> Spring-Security-Bausteinen.

---

## Teil F — Stand der Technik (Konventionen, die ein Eigenbau einhalten sollte)

| Thema | Praxis | Verhältnis zu Spec 010 |
| --- | --- | --- |
| **Opak statt JWT** | Ein PAT muss sofort widerrufbar sein; ein selbsttragendes JWT ist es nicht, ohne eine Sperrliste einzuführen — dann ist der Zustandsvorteil weg | Spec 010 entscheidet opak. **Deckt sich** |
| **Hashing** | Für **hochentropische** Secrets ein schneller Hash (SHA-256); bcrypt/Argon2 sind hier ein Performance-Antipattern, weil sie *jeden* Request verlangsamen und der Schutz aus der Entropie kommt, nicht aus der Rechenzeit. **Achtung Provenienz:** das ist Konsens der Praxisliteratur (u. a. `apikeys.guide`), **nicht** ein Zitat aus einem OWASP-Cheat-Sheet — die OWASP-Empfehlung Argon2id betrifft *Passwörter* | Spec 010 speichert SHA-256 des vollständigen Tokens. **Deckt sich — und Spec 010 führt dieselbe Begründung selbst**: „a fast cryptographic hash is correct (bcrypt-class slowness is for low-entropy passwords)". Kein Salt (`token_hash` = SHA-256-Hex des vollständigen Tokens), was bei ~256 Bit Entropie und Lookup über die separate, nicht geheime `tokenId` vertretbar ist |
| **Entropie** | ≥ 128 Bit Zufall aus einem CSPRNG | **Erfüllt und übererfüllt:** Spec 010 nutzt ~32 Byte URL-safe Base64 für das Secret und beziffert das in den *Security Considerations* mit „~256 bits of entropy"; die `tokenId` sind ~12 Byte (~96 Bit) aus `SecureRandom` |
| **Vendor-Prefix** | GitHub (`ghp_`), GitLab (`glpat-`) — damit Secret-Scanner geleakte Tokens erkennen. Es gibt zusätzlich einen **Standard**, der praktisch ignoriert wird: **RFC 8959**, „The `secret-token` URI Scheme" (Informational, Januar 2021, M. Nottingham) | Spec 010 wählt `oe_<typeCode>_<tokenId>_<secret>`, ausdrücklich analog zu `ghp_`. **Deckt sich** mit der Praxis; RFC 8959 wäre die Alternative und ist im Spec nicht erwähnt |
| **Lookup-Teil im Klartext** | Ein nicht geheimer Teil im Token (hier `tokenId`) erlaubt den Datenbanktreffer ohne Tabellenscan über alle Hashes | Spec 010 macht das. **Deckt sich** |
| **Konstantzeit-Vergleich** | Hash-Vergleich in konstanter Zeit | Spec 010 fordert es. **Deckt sich** |
| **Einmalige Anzeige** | Klartext wird genau einmal bei Erzeugung gezeigt, danach nie wieder | In Spec 010 vorhanden (`ApiKeyCreatedDto`-Analogie) |
| **Live-Rechte** | Deaktivierter Nutzer ⇒ Token wirkungslos, ohne Widerruf jedes Tokens | Spec 010s Kernregel + `PrincipalDirectory`. **Deckt sich**, und die Implementierung existiert schon |
| **Ablauf & Rotation** | Ablaufdatum als Default (Authentik: 360 Tage), Rotation ohne Downtime, „zuletzt benutzt"-Anzeige zur Aufräumhilfe | `expires_at` und `last_used_at` sind im Datenmodell von Spec 010 enthalten (`last_used_at` wird bei jeder erfolgreichen Validierung geschrieben). **Offen:** kein *Default*-Ablauf (Ablauf ist optional) und keine Rotation |

---

## Teil G — Berührungspunkte mit gelandeten und offenen Specs

- **Spec 019 (`AuthenticationType`)** — ein PAT-Aufrufer fällt heute in keine der fünf Konstanten
  sauber: er ist kein `JWT_ACCOUNT`, und `API_KEY` beschreibt die alte Infrastruktur. Die in Spec 019
  dokumentierte Erweiterbarkeit (neue Konstanten in Minor-Releases erlaubt) ist genau für diesen Fall
  vorgesehen; ein PAT-Spec müsste `PAT`/`USER_TOKEN` bzw. `SERVICE_TOKEN` ergänzen — und weil Spec 019
  Allow-Listen vorschreibt, ist das für Consumer verkraftbar.
- **Spec 017 (`getRoles()`)** — der PAT-Principal muss seine Rollen als `ROLE_*`-Authorities setzen,
  sonst sieht Anwendungscode sie nicht. `ResolvedPrincipal.roles()` liefert sie prefixfrei; das
  Mapping gehört in den Introspector.
- **`docs/TODO.md`: „Extract an integration module for API keys, PATs & webhooks"** — die Modulfrage
  ist bereits notiert und wäre mit einem PAT-Spec zu verbinden.
- **`docs/TODO.md`: „SCIM Group→Role mapping"** — `UserEntityPrincipalDirectory` liefert heute
  **leere** `roles()`/`groups()`. Für USER-PATs mit Rollen ist das der eigentliche Blocker: ohne
  dieses TODO ist ein USER-PAT rechtelos.
- **`docs/TODO.md`: „Property toggles"** — ein Token-Modul braucht Eigenschaften und stößt damit auf
  die dort offene Namenskonvention.

---

## Teil H — Optionen und Bewertung

| Option | Aufwand | Passt für SERVICE | Passt für USER-PAT | Betriebslast |
| --- | --- | --- | --- | --- |
| **1. Eigenbau auf `OpaqueTokenIntrospector`** (= Spec 010) | Mittel bis hoch: Entität, Migration, Ausgabe/Widerruf, Introspector, Verwaltungs-API | ja | ja | keine zusätzliche Infrastruktur |
| **2. Spring Authorization Server** (1.5.8 für Boot 3.5) | Hoch: zweites IdP betreiben | ja | schlecht — kein PAT-Modell, kein Nutzer-Self-Service | hoch |
| **3. Authentik-Tokens** (Service Accounts + App Passwords) | Niedrig: JWT-Kette existiert | ja, gut | schlecht — Self-Service liegt im IdP, nicht in unserer Anwendung | keine zusätzliche |
| **4. Bibliothek nehmen** | — | — | — | **entfällt: es gibt keine** |

Die naheliegende Kombination, die aus der Analyse folgt und im Design zu prüfen wäre: **Option 3 für
Maschinen, Option 1 nur für den Nutzerfall** — das würde den `SERVICE`-Zweig aus Spec 010
möglicherweise ganz einsparen und den Spec halbieren. Spec 010 hat diese Trennung nicht erwogen,
weil es die Vereinheitigung beider Fälle zum Ziel hatte.

---

## Offene Fragen für die Entscheidung

> **Nachtrag:** Die Fragen 1 und 2 sind im Abschnitt „Entscheidung vom 2026-09-10" beantwortet, und
> die Antwort hat den Gegenstand verschoben — der nächste Schritt ist nicht PAT, sondern Token
> Exchange. Die Fragen bleiben als Protokoll des Ausgangszustands stehen.

1. **Ein Mechanismus oder zwei?** Spec 010 vereinheitlicht `SERVICE` und `USER`. Wenn Authentik den
   Maschinenfall ohnehin abdeckt, ist die Vereinheitlichung ein Selbstzweck — dann bleibt nur PAT.
2. **Ersetzt PAT die `ApiKey`-Infrastruktur oder tritt es daneben?** Spec 010 sagt ersetzen
   (Breaking Change). Bei 1.4.0 mit dokumentierten Upgrade-Pfaden ist das teurer als im Juni.
3. **Wohin gehört das Modul?** `core` oder das im TODO notierte Integrationsmodul für API-Keys, PATs
   und Webhooks.
4. **Reihenfolge gegenüber „SCIM Group→Role mapping":** ohne Rollen aus dem `PrincipalDirectory` ist
   ein USER-PAT rechtelos. Rollen zuerst oder PAT zuerst mit leeren Rollen?
5. **Ablauf verpflichtend?** Spec 010 macht `expires_at` optional; Authentik setzt für Service-Account-Tokens 360 Tage als Default. Ein unbefristetes PAT ist ein dauerhaftes Risiko.
6. **Wird Spec 010 fortgeschrieben oder ersetzt?** Es ist inhaltlich tragfähig, aber in Annahmen
   veraltet (Version, Modulstruktur, zwei erledigte Open Questions).

---

## Entscheidung vom 2026-09-10 (`/grill-me` zu Fragen 1 und 2)

### Was entschieden ist

1. **Zwei Mechanismen, nicht einer.** API-Key und PAT sind verschiedene Gegenstände: automatisierter
   Zugriff auf *Systemdaten* gegenüber Nutzern, die *ihre* Daten verknüpfen. Die Vereinheitlichung in
   Spec 010 war damit Selbstzweck. Frage 1 ist beantwortet.
2. **Frage 2 löst sich auf.** Ohne PAT wird die `ApiKey`-Infrastruktur nicht ersetzt — sie bleibt
   unverändert, ebenso ihre Nutzung in `spring-services-mcp`.
3. **PAT wird zurückgestellt, Spec 010 geparkt.** Der Anwendungsfall, der ihn motivierte („Nutzer
   verknüpft seine Daten zwischen den Apps"), ist der **Online-Fall** — und den deckt **Token
   Exchange (RFC 8693)** ab. PAT bleibt für den **Offline-Fall** relevant (nächtliche Synchronisation,
   CLI, externe Tools wie n8n); Spec 010 ist inhaltlich tragfähig, nur nicht der nächste Schritt.
4. **Nächster Schritt: saubere Implementierung und Dokumentation von Token Exchange** — ausgearbeitet
   als **Spec 020** (`docs/specs/020-token-exchange/`), beide Hälften: eingehende Erkennung in `core`,
   ausgehender Tausch in einem neuen optionalen Modul.

### Die Anforderung, an der Token Exchange gemessen wird

Backend B muss erkennen, dass nicht der Nutzer direkt ruft, sondern ein **Zwischensystem im Auftrag
eines Nutzers** — möglichst mit Identifikation dieses Systems — und das muss auf **authentik und
Keycloak** gleichwertig funktionieren.

### Wie das erfüllt wird — und wie nicht

| Merkmal | Trägt es? |
| --- | --- |
| `act`-Claim (RFC 8693 Delegation) | **Nur authentik**, ab 2026.8, Form `{"sub": "…", "act": {"sub": "actor"}}`. Für Keycloak weder in der 26.2-Ankündigung noch im Identity-Chaining-Post von 26.5 belegt. **Als herstellerneutrales Merkmal unbrauchbar** |
| `client_id` / `azp` | **Beide.** `client_id` ist für JWT-Access-Tokens nach RFC 9068 vorgeschrieben, Keycloak liefert zusätzlich `azp`. Das ist das gewählte Merkmal |
| Bedeutung der Client-ID | Die Library kann sie nicht deuten — „eigenes Frontend" gegenüber „fremdes Zwischensystem" ist Konfiguration. Weil **jedes Backend genau ein direktes Frontend hat**, ist es *ein Wert*, keine Liste: `client_id == eigenes Frontend` ⇒ direkter Nutzer, sonst ⇒ Zwischensystem. Pflege von Fremdsystem-Metadaten wird eine spätere Spec |

### Der Teil, der noch nicht entschieden ist — und die Lücke, die dadurch offen bleibt

**Audience-Validierung** ist in `docs/TODO.md` als *wichtig* eingetragen, nicht sofort umgesetzt.
Solange sie fehlt, ist Token Exchange **umgehbar**: ein weitergereichtes Frontend-Token von A wird von
B angenommen (gemeinsamer Issuer, kein `aud`-Check) und nach der Client-ID-Regel brav als
„Zwischensystem" einsortiert. Die Erkennung ist dann Beschriftung, keine Kontrolle. Das ist bewusst in
Kauf genommen; die Reihenfolge ist erst Token Exchange, dann die Erzwingung.

### Was die Umsetzung *nicht* bauen muss (geprüft, nicht vermutet)

`spring-security-oauth2-client` **6.5.10 — also die hier schon gemanagte Version — enthält die
Client-Seite von RFC 8693 vollständig**:

- `AuthorizationGrantType.TOKEN_EXCHANGE`
- `TokenExchangeOAuth2AuthorizedClientProvider` (+ reaktive Variante)
- `TokenExchangeGrantRequest`, `TokenExchangeGrantRequestEntityConverter`
- `RestClientTokenExchangeTokenResponseClient`, `DefaultTokenExchangeTokenResponseClient`
- dazu `JwtBearer*` (RFC 7523) für den Keycloak-26.5-Pfad „Identity Chaining"

Der ausgehende Teil ist damit **Konfiguration plus schmaler Kleber** (Subject-Token aus dem laufenden
Request beschaffen, Zieldienst wählen, Fehler- und Cache-Verhalten), nicht eine Protokoll-
implementierung. Die eingehende Seite — Zwischensystem erkennen und benennen — ist neue Claim-Ober-
fläche in `AuthService`/`SecurityConfig` und der eigentliche Neubau.

### Offene Punkte für die Token-Exchange-Spec

1. **Authentik-Delegation praktisch verifizieren.** Die Doku verlangt einen authentik-*Actor*, dessen
   Parent-User dem Nutzer des Subject-Tokens entspricht. Ob damit „Service X handelt für beliebige
   Nutzer" überhaupt abbildbar ist, ist offen — und `act` ist ohnehin nicht das gewählte Merkmal.
2. **Authentik-Version:** Delegation ab 2026.8; verifiziert wurde in Spec 015 gegen 2026.5.4.
3. **Audit-Log.** Spec 005/008 schreibt den Nutzer. Handelt ein Zwischensystem, gehört es in den
   Datensatz — bisher ungeklärt.
4. **Verhältnis zu `AuthenticationType`** (Spec 019): Delegation ist orthogonal zum Mechanismus. Eine
   eigene Auskunft ist wahrscheinlich richtiger als eine sechste Enum-Konstante.
5. **Keycloak-Grenze:** Standard Token Exchange deckt nur *Internal-Internal* ab; Tokens fremder IdPs
   und Impersonation bleiben im Legacy-Feature. Für „drei Apps, ein IdP" reicht das.

## Quellen

**Lokal geprüft** (Jar-/Quellinventar, keine Websuche): `spring-security-core`, `-web`,
`-oauth2-resource-server` 6.5.10; **`spring-security-oauth2-client` 6.5.10** (RFC-8693-Client-Seite:
`AuthorizationGrantType.TOKEN_EXCHANGE`, `TokenExchangeOAuth2AuthorizedClientProvider`,
`TokenExchangeGrantRequest`, `RestClientTokenExchangeTokenResponseClient`, `JwtBearer*`);
`spring-security-oauth2-authorization-server` 7.1.1 (`OAuth2TokenFormat.REFERENCE` per `javap`);
**`spring-boot-autoconfigure` 3.5.14** (`OAuth2ResourceServerJwtConfiguration.getValidators(...)` —
Audience-Validator nur bei gesetzter `audiences`-Eigenschaft);
`net.skobow:apikey-authentication-spring-boot-starter` Metadaten und Release-Daten auf
`repo1.maven.org`; RFC 8959 im Volltext von `rfc-editor.org`; Repo-Stand von `spring-services` selbst
(keine `aud`-/`azp`-/`client_id`-Auswertung, keine aufgezeichneten Authentik-Tokens).

**Web:**

- [API key authentication support · Issue #17563 · spring-projects/spring-security](https://github.com/spring-projects/spring-security/issues/17563)
- [What's New in Spring Security 7.0](https://docs.spring.io/spring-security/reference/7.0-SNAPSHOT/whats-new.html)
- [OAuth 2.0 Resource Server Opaque Token :: Spring Security](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/opaque-token.html)
- [Service accounts | authentik](https://docs.goauthentik.io/sys-mgmt/service-accounts/)
- [Machine-to-Machine (M2M) authentication | authentik](https://docs.goauthentik.io/add-secure-apps/providers/oauth2/machine_to_machine/)
- [OAuth 2.0 provider | authentik](https://docs.goauthentik.io/add-secure-apps/providers/oauth2/)
- [skobow/apikey-authentication-spring-boot-starter](https://github.com/skobow/apikey-authentication-spring-boot-starter)
- [42BV/api-key-authentication](https://github.com/42BV/api-key-authentication)
- [Hashing & Storage — apikeys.guide](https://apikeys.guide/docs/security/hashing-and-storage)
- [Key Generation — apikeys.guide](https://apikeys.guide/docs/implementation/key-generation)
- [Password Storage Cheat Sheet — OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) (betrifft Passwörter, nicht API-Keys)
- [How We Create API Tokens Using Spring Boot — Qovery](https://www.qovery.com/blog/how-we-create-api-tokens-using-spring-boot)
- [RFC 8959 — The "secret-token" URI Scheme](https://www.rfc-editor.org/rfc/rfc8959.txt)
- [RFC 8693 — OAuth 2.0 Token Exchange](https://datatracker.ietf.org/doc/html/rfc8693)
- [Token exchange | authentik](https://docs.goauthentik.io/add-secure-apps/providers/oauth2/token_exchange/) — Impersonation vs. Delegation, `act`-Claim, Actor-Voraussetzungen, ab 2026.8
- [Standard Token Exchange is now officially supported in Keycloak 26.2](https://www.keycloak.org/2025/05/standard-token-exchange-kc-26-2) — RFC-8693-Konformität, Internal-Internal, Legacy-Feature für Impersonation
- [JWT Authorization Grant and Identity Chaining in Keycloak 26.5](https://www.keycloak.org/2026/01/jwt-authorization-grant) — RFC 7523 (Preview) plus Token Exchange für Cross-Domain-Chaining
- [Setting an Audience in Keycloak — digital blueprint handbook](https://handbook.digital-blueprint.org/frameworks/relay/howtos/keycloak_audience/) und [Audience Mapper adds UUID of a Client rather than "Client ID"](https://groups.google.com/g/keycloak-user/c/2p41VjxYrf8) — Audience-Mapper und der UUID-Stolperstein
