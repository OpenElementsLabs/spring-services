# Idee: Löschnachweis-Service (Deletion Proof Service)

> **Status:** Konzept / Ausgangspunkt für spätere Ausarbeitung (ADR + Spec)
> **Stand:** 2026-06-23
> **Kontext:** Open Elements `spring-services` — Java/Spring Backend mit WebSocket
> und PAT/API-Token-Mechanik (bereits vorhanden).

Dieses Dokument fasst eine Diskussion zur Idee eines DSGVO-Löschnachweis-Service
zusammen. Es dient als Ausgangsbasis, um die Idee später als eigenständiger
Service weiterzuverfolgen. **Es ist noch keine Entscheidung und keine
Implementierung** — die nächsten Schritte (ADR, Spec) stehen unten.

> ⚠️ **Disclaimer:** Die folgenden rechtlichen Einordnungen sind fachliche
> Zusammenfassungen, **keine Rechtsberatung**. Vor produktiver Umsetzung ist eine
> juristische Prüfung (Datenschutzbeauftragte:r / Fachanwalt) erforderlich.

---

## 1. Problem & Idee

Die DSGVO verlangt, dass ein Verantwortlicher die Einhaltung der
Datenschutzgrundsätze **nachweisen kann** (Rechenschaftspflicht, Art. 5 Abs. 2).
Es gibt aber **kein** gesetzlich vorgeschriebenes Dokument namens
"Lösch-Report" / "Löschbescheinigung".

Die Idee: Ein **Löschnachweis-Service**, der Lösch- und Anonymisierungsvorgänge
unveränderlich festhält und über den ein **externes System (per WebSocket + PAT)
nachfragen kann**, ob die Daten zu einem bestimmten Identifier gelöscht bzw.
anonymisiert wurden — **ohne dass dabei jemals Klartext-PII gespeichert wird**.

Kernmechanik:

1. Beim Löschen/Anonymisieren wird die identifizierende Information (z. B.
   E-Mail) **per HMAC gehasht** und als Identifier im Nachweis-Datensatz abgelegt.
2. Ein externes System liefert später einen Identifier (den es ohnehin kennt),
   der Service hasht ihn und schlägt im Register nach.
3. Antwort über WebSocket: `deleted` / `anonymized` / `restricted` + Zeitpunkt —
   ganz ohne Klartext-PII.

Das löst das **Löschnachweis-Paradox**: Man kann die Löschung beweisen, ohne die
Daten aufzubewahren, deren Löschung man beweisen will.

---

## 2. Rechtliche Grundlagen (DSGVO)

| Artikel               | Bedeutung für den Service                                                                                  |
|-----------------------|------------------------------------------------------------------------------------------------------------|
| **Art. 5 Abs. 2**     | Rechenschaftspflicht (Accountability) — der eigentliche Grund für den Nachweis.                            |
| **Art. 17**           | Recht auf Löschung ("Recht auf Vergessenwerden").                                                          |
| **Art. 18**           | Einschränkung der Verarbeitung (Sperrung) — wichtig, wenn Aufbewahrungspflichten eine Löschung verhindern. |
| **Art. 12 Abs. 3**    | Bestätigung an die betroffene Person (i. d. R. binnen eines Monats).                                       |
| **Art. 19**           | Mitteilungspflicht gegenüber Empfängern, an die Daten weitergegeben wurden.                                |
| **Art. 25 / Art. 32** | Pseudonymisierung als ausdrücklich vorgesehene Schutzmaßnahme (Grundlage für HMAC-Ansatz).                 |

**Wichtig:** Ein Löschnachweis darf **nicht die gelöschten personenbezogenen
Daten selbst** enthalten — sonst hebt er die Löschung wieder auf. Dokumentiert
wird die *Tatsache und die Umstände* der Löschung, nicht die Inhalte.

---

## 3. Scope: Was wird gelöscht? (Personen vs. Firmen)

Die DSGVO schützt **ausschließlich natürliche Personen**. Eine juristische Person
(GmbH, AG, e. V.) hat **kein** Recht auf Löschung nach Art. 17.

Die Grenze ist trotzdem fließend, weil "Firmendaten" oft PII natürlicher Personen
enthalten:

| Fall                                                      | DSGVO-relevant?                     |
|-----------------------------------------------------------|-------------------------------------|
| Ansprechpartner/Kontaktperson (`max.mustermann@firma.de`) | ✅ Ja                                |
| Einzelunternehmer / Freiberufler                          | ✅ Ja (ist eine natürliche Person)   |
| Ein-Personen-GmbH / namentlicher Geschäftsführer          | ⚠️ Teilweise (Person ja, GmbH nein) |
| Generische Postfächer (`info@firma.de`)                   | ❌ i. d. R. nein                     |
| Reine Stammdaten einer juristischen Person                | ❌ nein                              |

**Konsequenz:** Man löscht nie "die Firma", sondern die personenbezogenen
Anteile (Kontaktpersonen). Falls "Firma löschen" angeboten wird, ist das ein
**freiwilliges/vertragliches Feature, kein DSGVO-Recht**.

---

## 4. Wichtiger Bremsklotz: Aufbewahrungspflichten

Selbst bei einer natürlichen Person darf oft **nicht sofort alles** gelöscht
werden. **§ 257 HGB / § 147 AO** verlangen z. B. 10 Jahre Aufbewahrung für
Rechnungen und Buchungsbelege. Das überschreibt Art. 17.

Korrekte Reaktion ist dann nicht Löschung, sondern **Einschränkung der
Verarbeitung / Sperrung (Art. 18)** — Daten werden eingefroren und erst nach
Fristablauf endgültig gelöscht. Im Modell abgebildet durch
`method = restricted` + `restricted_until`.

---

## 5. Pseudonymisierung: Klartext vermeiden

Im permanenten Nachweis **kein Klartext** (kein Name, keine E-Mail). Stattdessen
ein **keyed HMAC** mit serverseitigem Secret/Pepper:

```
identifier = HMAC-SHA256(key = SERVER_SECRET, message = normalize(email))
```

- **Warum HMAC statt plain SHA-256:** E-Mail-Adressen haben zu wenig Entropie —
  ein einfacher Hash ist per Wörterbuch/Enumeration trivial umkehrbar und gilt
  damit selbst wieder als (schwach geschützte) PII. Mit Pepper ist es echte
  Pseudonymisierung im Sinne von Art. 25 / Art. 32.
- **Restspannung:** Auch ein HMAC ist mit Key reversibel und damit streng
  genommen pseudonyme — nicht anonyme — Daten. Das ist der von der DSGVO
  gewünschte Mittelweg zwischen Nachweisbarkeit und Datenminimierung und deutlich
  besser als Klartext.
- **Mehrere Identifier pro Subjekt** (E-Mail, Telefon, Kundennummer …), damit der
  externe Check funktioniert, egal womit gefragt wird.

---

## 6. Das Datenmodell

Ergebnis der Diskussion. Bewusst schlank gehalten; jedes Feld hat eine konkrete
Begründung.

### `deletion_record` — unveränderlich (immutable, append-only)

Wird einmal beim Lösch-/Anonymisierungs-/Sperrvorgang erstellt und **danach nie
wieder geändert**. Enthält nur Fakten, die zum Zeitpunkt des Vorgangs feststehen.

```
deletion_record   (immutable, append-only)
  ├─ record_ref         UUID — interne Vorgangs-Referenz
  ├─ identifiers[]      { type, hmac }   — kein Klartext
  │     ├─ type         email | phone | customer_no | ust_id | …
  │     └─ hmac         HMAC-SHA256(SERVER_SECRET, normalize(value))
  ├─ method             hard_delete | anonymization | restricted
  │                     (crypto_shredding später ergänzbar — siehe Abschnitt 11)
  ├─ restricted_until   <date> | null    (nur bei method = restricted, Art. 18)
  ├─ data_categories[]  WAS gelöscht wurde, nicht die Inhalte
  │                     z. B. ["contact_data", "order_history"]
  ├─ affected_systems[] db | backups | meilisearch | logs | cache | …
  └─ deleted_at         <timestamp>      Zeitpunkt des Vorgangs
```

### `deletion_event` — append-only, Folge-Ereignisse

Alles, was **nach** der Löschung passiert (und den unveränderlichen Datensatz
deshalb nicht anfassen darf), kommt in einen separaten Event-Log mit Verweis auf
`record_ref`.

```
deletion_event   (append-only)
  ├─ id             UUID
  ├─ record_ref     → deletion_record.record_ref
  ├─ type           recipient_notification (Art. 19)
  │                 | subject_confirmation (Art. 12 Abs. 3)
  ├─ recipient      <text> | null   (nur bei recipient_notification, z. B. "CRM-System X")
  └─ occurred_at    <timestamp>
```

### Designentscheidungen (und warum)

| Entscheidung                                    | Begründung                                                                                                                                                                      |
|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Immutable / append-only**                     | Ein Nachweis, der nachträglich änderbar ist, ist kein Nachweis.                                                                                                                 |
| **`deletion_event` getrennt**                   | Empfänger-Benachrichtigung (Art. 19) und Bestätigung an Betroffene (Art. 12 Abs. 3) passieren *nach* der Löschung → dürfen den immutablen Datensatz nicht ändern.               |
| **`method` statt `status` + `deletion_method`** | Beide Felder waren dieselbe Achse; ein Feld reicht. `restricted` ist streng genommen keine "Methode" — falls das stört, Feld in `action` umbenennen.                            |
| **`crypto_shredding` vorerst nicht im Scope**   | Start mit `hard_delete` + `anonymization` (+ `restricted`). Crypto-Shredding ist ein rein additiver weiterer `method`-Wert und kann später folgen — v. a. für die Backup-Frage. |
| **`deleted_at` statt `created_at`**             | Semantisch der Zeitpunkt der Löschung. (Bei häufigem `restricted` ggf. neutraler `processed_at`.)                                                                               |
| **Kein `legal_basis`**                          | Die Rechtsgrundlage ist Eigenschaft des *Auslösers/Antrags*, nicht des Löschvorgangs — gehört nicht in den Nachweis dupliziert.                                                 |
| **Kein `subject_type` (3 Werte)**               | `sole_trader` vs. `natural_person` ist rechtlich dasselbe (beide natürliche Personen) → Over-Engineering.                                                                       |
| **Kein `gdpr_relevant`**                        | Bewusst entfernt, um das Modell schlank zu halten. (Falls der Service selbst DSGVO-Scope-Fragen beantworten muss, könnte es zurückkommen.)                                      |

---

## 7. Entity-Annotation: PII im Code markieren

Die Verbindung zwischen den Entities und dem `deletion_record`. Ziel: die
Annotationen sind die **einzige Wahrheitsquelle** und steuern sowohl die
*Ausführung* der Löschung als auch die *Erzeugung* des Nachweises — so kann keine
Drift zwischen "was wird gelöscht" und "was steht im Nachweis" entstehen.

### Drei Annotationen, drei Fragen

| Annotation       | Ebene       | Beantwortet                                                                   |
|------------------|-------------|-------------------------------------------------------------------------------|
| `@DataSubject`   | Klasse      | "Diese Entity enthält personenbezogene Daten einer natürlichen Person."       |
| `@DataSubjectId` | Feld/Getter | "Dieses Feld identifiziert die Person → wird zum HMAC-Identifier."            |
| `@PersonalData`  | Feld/Getter | "Dieses Attribut ist PII → so wird es gelöscht, und das ist seine Kategorie." |

```java

@Retention(RUNTIME)
@Target(TYPE)
public @interface DataSubject {
}

@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface DataSubjectId {
    String value();        // "email", "phone", "customer_no" — Freitext, siehe unten
}

@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface PersonalData {
    DataCategory category() default DataCategory.CONTACT_DATA;

    ErasureStrategy onErasure() default ErasureStrategy.DELETE;
}
```

### Enums — exakt am `deletion_record`-Modell ausgerichtet

```java
public enum ErasureStrategy {
    DELETE,         // → method = hard_delete
    ANONYMIZE,      // → method = anonymization
    RETAIN          // → method = restricted (Aufbewahrungspflicht, Art. 18)
    // CRYPTO_SHRED später ergänzbar → method = crypto_shredding
}
```

`ErasureStrategy` bildet 1:1 auf `deletion_record.method` ab, `DataCategory` auf
`data_categories[]`. Dadurch ist der Nachweis automatisch aus den Annotationen
ableitbar.

### DELETE vs. ANONYMIZE — wann was (und warum)

Zwei Ebenen, auf denen die Entscheidung fällt:

**1. Entity-Ebene (Fremdschlüssel):** Wenn abhängige Zeilen überleben müssen,
kann die Wurzel nicht hart gelöscht werden. Beispiel: `User (1) ──< Review (n)` —
löscht man den User, gehen entweder die Reviews mit (Content/Threads kaputt) oder
die FKs verwaisen. Lösung: User-PII anonymisieren, Reviews bleiben als „Gelöschter
Nutzer". Mit durchgängig **technischen IDs** ist dieser Fall bei uns aber meist
entschärft.

**2. Feld-Ebene (Spalten-Constraints):** Hier tritt Anonymisierung in einem
normalisierten Schema *tatsächlich* am häufigsten auf. Der Constraint entscheidet,
welche Strategie überhaupt möglich ist:

| Spalten-Constraint        | Was geht                                    | Strategie                          |
|---------------------------|---------------------------------------------|------------------------------------|
| nullable, nicht unique    | auf `NULL` setzen                           | `DELETE` (Feld nullen) genügt      |
| `NOT NULL`, nicht unique  | Platzhalter-Konstante                       | `ANONYMIZE` → z. B. `"[gelöscht]"` |
| `UNIQUE` (± `NOT NULL`)   | eindeutiger, generischer Wert aus tech. ID  | `ANONYMIZE` → ID-abgeleiteter Wert |

Konkrete Beispiele:

- **`email` (`NOT NULL UNIQUE`)** — der klassische Fall: kann weder genullt werden
  (`NOT NULL`) noch konstant überschrieben werden (`UNIQUE`-Kollision beim zweiten
  gelöschten Nutzer). → eindeutiger generischer Wert wie
  `deleted+{id}@anonymized.invalid`. Die TLD `.invalid` (RFC 2606) garantiert, dass
  nie versehentlich eine Mail rausgeht.
- **`username` (`NOT NULL UNIQUE`)** → `deleted_user_{id}`.
- **`firstName` (`NOT NULL`)** → konstanter Platzhalter `"[gelöscht]"`.
- **`phone` (nullable)** → einfach `NULL`; hier reicht `DELETE`.

**Merksatz:** nullable → `DELETE` genügt; `NOT NULL`/`UNIQUE` → `ANONYMIZE` mit
generischem (ggf. ID-abgeleitetem) Wert. Der generische Wert darf **keinerlei PII**
enthalten und nicht auf die Person rückführbar sein — eine technische ID ist dafür
unkritisch, da sie selbst keinen Personenbezug trägt.

### Beispiel

```java

@Entity
@DataSubject
public class Customer {

    @Id
    private UUID id;

    @DataSubjectId(IdentifierTypes.EMAIL)
    @PersonalData(onErasure = ErasureStrategy.ANONYMIZE)
    private String email;

    @PersonalData                                   // = CONTACT_DATA + DELETE
    private String firstName;

    @PersonalData                                   // gleiche category → kollabiert im Nachweis
    private String lastName;

    @PersonalData(category = DataCategory.HEALTH_DATA)   // sensible Kategorie, Strategie = DELETE
    private String medicalNotes;

    @PersonalData(category = DataCategory.BILLING_DATA, onErasure = ErasureStrategy.RETAIN)
    private String billingAddress;                  // § 257 HGB → nicht löschen, sperren

    private BigDecimal accountBalance;              // keine PII → keine Annotation
}
```

### Wichtige Designentscheidungen

- **`@PersonalData`, nicht `@Pii`** — "personal data" ist der DSGVO-Begriff;
  "PII" ist US-zentrisch und rechtlich nicht deckungsgleich.
- **An den persistierten Zustand annotieren.** Die Löschung wirkt auf das, was
  real in der DB liegt (`firstName`/`lastName`), nicht auf abgeleitete Werte
  (`getFullName()` — da gibt es nichts zu löschen). `@Target({FIELD, METHOD})`
  erlaubt den Getter dort, wo JPA **Property-Access** nutzt; der Service muss den
  JPA-Access-Type der Entity respektieren.
- **Erasure ≠ Auskunft.** Löschung (Art. 17) wirkt auf den persistierten Zustand;
  Auskunft/Export (Art. 15/20) auf die logische Sicht der Person (auch
  Abgeleitetes). Falls Letzteres gebraucht wird, ist das eine *separate*
  Annotation/Service — nicht mit der Löschung vermischen.
- **`category` default `CONTACT_DATA`** — der häufigste Fall wird annotationsfrei
  kurz. Tradeoff: "bewusst Kontaktdaten" und "Kategorie vergessen" werden
  ununterscheidbar → sensible Felder müssen explizit kategorisiert werden
  (per ArchUnit-Regel erzwingen, siehe unten).
- **`DataSubjectId` ist ein String-Key, kein Enum.** Identifier-Typen sind
  konsumentenspezifisch (`customer_no`, `membership_no`, `ust_id` …). Ein
  geschlossenes Enum in der Library wäre zu starr — eine App müsste es erweitern.
  Gängige Typen als Konstanten (`IdentifierTypes.EMAIL`), eigene als Freitext.
  Das ist der einzige bewusst *offene* Teil des Modells.

### Durchsetzung

- **Ort:** Annotationen + Enums gehören in die **Basis-Library / das
  `oe_spring_services`-Starter-Modul**, damit alle Backends dasselbe Vokabular
  nutzen.
- **ArchUnit-Regel:** "Jede `@DataSubject`-Entity hat mind. ein
  `@DataSubjectId`-Feld" und "verdächtige Feldnamen (`*email*`, `*health*`,
  `*medical*`, `*religion*`) ohne explizite `@PersonalData`-`category` schlagen
  fehl". Verhindert den häufigsten DSGVO-Fehler: PII einbauen ohne sie zu
  markieren.

Prior Art: **Axon Framework** nutzt ein sehr ähnliches Annotationsmodell für
Crypto-Shredding.

---

## 8. Architektur: App ↔ Service & Invarianten

Aufteilung der Verantwortung zwischen der App (die die Daten hält) und dem neuen
Lösch-Service (der den Nachweis führt):

1. Die **App** führt eine domänenspezifische Normalisierung durch und schickt
   `{ value, type }` (Typ als Freitext-String) an den Service.
2. Der **Service** wendet bei *bekanntem* Typ nochmals seine kanonische
   Normalisierung an und berechnet den HMAC.

### Warum diese Aufteilung

- **HMAC gehört zwingend in den Service.** Das `SERVER_SECRET` darf **nur** dort
  liegen — hätte jede App den Schlüssel, wäre die Pseudonymisierung wertlos.
- **Choke-Point-Vorteil:** Schreiben (Löschung) und Abfragen laufen beide durch
  denselben Service. Macht der Service für bekannte Typen die finale, autoritative
  Normalisierung, matchen die Hashes beider Pfade **garantiert**.

### Die drei Invarianten

1. **Secret + HMAC nur im Service.** Keine App berechnet je selbst einen Hash.
2. **Klartext nur transient.** PII geht über TLS rein, wird **sofort** gehasht und
   danach verworfen — **niemals** persistiert, geloggt, in Stacktraces/Errors.
   Gilt für Schreib- *und* Abfrage-Pfad.
3. **Normalisierung deterministisch über beide Pfade.** Service-Normalisierer sind
   **idempotent** (App + Service == nur Service). Verantwortung nach Typ:
    - **Bekannter Typ** → Service autoritativ; App-Normalisierung redundant, aber
      dank Idempotenz unschädlich.
    - **Unbekannter Freitext-Typ** → Service reicht durch; **Konsistenz liegt beim
      Caller** (App muss zu Lösch- und Abfragezeit identisch normalisieren).

### Ergänzung

Bei unbekanntem Typ gibt der Service eine **Warnung/Metrik** aus (nicht den Wert
loggen!), damit häufige neue Typen zu "bekannten" mit kanonischer Normalisierung
hochgezogen werden können.

---

## 9. Abfrage-Flow (extern, per WebSocket + PAT)

1. Externes System authentifiziert sich mit **PAT/API-Token** (passender Scope).
2. Es liefert einen Identifier (z. B. eine E-Mail), den es bereits kennt.
3. Service normalisiert + berechnet `HMAC-SHA256(SERVER_SECRET, value)`.
4. Lookup im Register über den Hash (Index auf `identifiers.hmac`).
5. Antwort über WebSocket:
   `{ status: "deleted" | "anonymized" | "restricted" | "active", deleted_at, record_ref }`.

Auch die betroffene Person selbst kann sich so bestätigen lassen: Sie nennt ihre
E-Mail → Service hasht → Match → Nachweis. Ohne Klartextspeicher.

---

## 10. Existierende Open-Source-Projekte / Vorbilder

Es gibt Projekte für **Teile** davon, aber keins, das genau dieses Modell
(immutabler, hash-indizierter, extern abfragbarer Lösch-Nachweis) abbildet.

| Projekt / Standard                     | Relevanz                                                                                                                               |
|----------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| **Ethyca Fides / Fidesops**            | OSS-Privacy-Plattform; orchestriert das *Ausführen* von Lösch-/Auskunftsanträgen über viele Systeme. Nicht der Nachweis-Teil.          |
| **Axon Framework** (Java)              | Crypto-Shredding-/Data-Protection-Modul für GDPR — Java-nah, gut zum Abschauen der Mechanik.                                           |
| **VeritasChain Protocol (VCP)**        | OSS-PoC: PII pro Subjekt verschlüsseln, Schlüssel bei Löschung vernichten, Hash-Chain bleibt für Audit intakt. Konzeptionell sehr nah. |
| **Kafka Crypto-Shredding** (Conduktor) | Muster für Crypto-Shredding in Event-Streams.                                                                                          |

### Normen / Referenzen für den Nachweis-Aufbau

Es gibt **kein** einzelnes Dokument, das einen "Erasure-Audit-Report" verbindlich
definiert. Die belastbaren Anker:

- **DSGVO** Art. 5 Abs. 2, 17, 18, 12 Abs. 3, 19, 30 — die rechtliche Pflicht.
- **NIST SP 800-88** ("Guidelines for Media Sanitization") — enthält eine konkrete
  **"Certificate of Sanitization"-Vorlage** (Methode, Tool, Verifikation,
  Validierung, beteiligte Personen). Deckt sich fast 1:1 mit `deletion_record`.
- **DIN 66398** — deutsche "Leitlinie zur Entwicklung eines Löschkonzepts";
  kanonische deutschsprachige Referenz für Löschkonzept + Löschnachweis.
- **ISO/IEC 27701** — Privacy Information Management; fordert ROPA inkl. Entsorgung
  und Verifikation der Löschung.

**Realismus-Check:** Das *abfragbare Hash-Register* ist eine eigene Ergänzung, die
keine dieser Normen vorschreibt — es ist die technische Umsetzung der
Nachweisbarkeit, keine Norm-Anforderung.

---

## 11. Offene Punkte / noch zu entscheiden

- [ ] `deleted_at` vs. `processed_at` (relevant, falls `restricted` häufig vorkommt).
- [ ] Soll `gdpr_relevant` (oder Trennung natürliche/juristische Person) doch
  gebraucht werden, um DSGVO-Scope-Fragen direkt am Register zu beantworten?
- [ ] Aufbewahrungsfrist des Nachweis-Registers selbst (es ist seinerseits
  pseudonym-personenbezogen) und dessen Lösch-/Rotationsregel.
- [ ] Key-Management für das HMAC-Secret (Rotation? Was passiert mit alten Hashes
  bei Key-Rotation?).
- [ ] Backups: Wann gilt eine Löschung als "vollständig"? (Backup-Rotationsfrist
  dokumentieren.)
- [ ] **Später:** `crypto_shredding` als zusätzlicher `method`-Wert / zusätzliche
  `ErasureStrategy` ergänzen — primär als Antwort auf die Backup-Frage
  (Schlüssel vernichten statt unerreichbare Backups mutieren). Bewusst aus dem
  ersten Scope herausgenommen; rein additiv, kein Modellbruch.
- [ ] Meilisearch-Index muss bei Löschung explizit mitbehandelt werden.
- [ ] Absicherung der Immutability: keine Update-Methode im Repository/Service +
  optional DB-Trigger (`BEFORE UPDATE/DELETE` wirft) für beide Tabellen.
- [ ] PAT-Scope-Definition für den Abfrage-Endpunkt.
- [ ] Normalisierungs-SPI: brauchen wir die erweiterbare Variante schon, oder
  reicht zunächst eine simple idempotente Map für `email`/`phone`?
- [ ] Registry "bekannter" Identifier-Typen + Metrik/Warnung bei unbekanntem Typ.
- [ ] ArchUnit-Regeln für PII-Annotationen (siehe Abschnitt 7) umsetzen.
- [ ] Auskunft/Export (Art. 15/20) als separater Service/Annotation — bewusst
  außerhalb dieses Lösch-Service halten.

---

## 12. Nächste Schritte

1. **ADR** — die Architekturentscheidung festschreiben:
    - Pseudonymisierung per HMAC statt Klartext
    - Immutable / append-only statt mutable
    - Pull über WebSocket/PAT statt Push
    - Trennung `deletion_record` (Fakt) vs. `deletion_event` (Folge-Ereignisse)
    - DSGVO-Nachweis nur für natürliche Personen; juristische Personen separat
    - Annotations-gesteuerte Löschung als einzige Wahrheitsquelle (Abschnitt 7)
    - App ↔ Service-Aufteilung + die drei Invarianten (Abschnitt 8)
    - Verworfene Alternative: Fides/Axon übernehmen statt eigener Komponente
2. **Spec** — das Wie:
    - JPA-Mapping (inkl. Index auf `identifiers.hmac`, Immutability-Absicherung)
    - Annotationen + Enums in der Basis-Library; `ErasureService` per Reflection
    - `DeletionService` mit Nachweis-Erzeugung
    - WebSocket-Query-Flow mit PAT-Scope
    - Anonymisierungs-/Löschstrategie inkl. Meilisearch und Backup-Frist
3. **Juristische Prüfung** vor produktiver Umsetzung.