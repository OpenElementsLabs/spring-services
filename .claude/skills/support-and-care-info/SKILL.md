---
name: support-and-care-info
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Background information about Open Elements' Support & Care offering — professional maintenance and support for critical open-source Java components (JUnit, Apache Maven, Log4j, Commons, Eclipse Temurin). Use when generating content about Support & Care, Java OSS maintenance, CRA compliance for open-source dependencies, or when the user mentions open-source support services, Java supply chain security, or OSS stewardship.
---

# Support & Care

## Logo

The Support & Care logo is available in this skill's folder in three formats:

- **support-and-care-01.svg** — SVG vector format (preferred)
- **support-and-care-01.png** — PNG with transparent background
- **support-and-care-01-w.png** — PNG white version for dark backgrounds

## Marketing Material

The official Support & Care flyer (English, 4 pages) is bundled with this skill:

- **support-and-care-flyer-en.pdf** — Reference for messaging, structure, statistics, and visuals. Use it as the canonical source when writing pitches, landing pages, or proposals about Support & Care, and as a template when generating new marketing material in the same visual language.

## Overview

Support & Care is Open Elements' core business offering: professional maintenance and support for critical open-source components in the Java ecosystem. Modern software consists of over 70% open-source components. Starting in 2027, the Cyber Resilience Act (CRA) makes manufacturers responsible for 100% of their software — including all OSS dependencies.

Support & Care addresses this by providing continuous maintenance, monitoring, and proactive care for the foundational Java components that enterprises depend on.

**Motto:** Open Source — aber richtig. / Open Source made right.

### Iceberg Metaphor

A common visual framing in Support & Care communication is the **iceberg**: your production code is the visible tip above the waterline, while the open-source dependencies it stands on form the much larger mass underwater. Most technical risk, compliance responsibility, and security exposure lives below the surface — and that is where Support & Care operates.

### The Invisible Dependencies Problem

The risk that Support & Care addresses is concrete and easy to illustrate:

- A simple Spring Boot project pulls in **70+ transitive dependencies** before a single line of business logic is written.
- Around **70 % of any modern Java codebase is open source** — code outside the manufacturer's direct control.
- These foundational components are often **maintained by individual developers in their spare time**, with no commercial backstop.
- **Framework support does not close this gap**: vendor support for Spring Boot, Quarkus, or Jakarta EE covers the middle layer but does not guarantee maintenance, patching, or compliance for the runtime, build tools, logging, testing, and utility libraries underneath.

Log4Shell (December 2021) was the canonical demonstration: a critical flaw in a deeply-embedded base library that vendor and framework channels could not surface fast enough to reach affected applications in time.

### History

Support & Care launched in **2024** with Apache Maven as its first supported component, and has been continuously expanded to cover the full set of business-critical Java base components listed below.

## Supported Components

### JUnit

- Over 1 billion downloads per month
- Used by approximately 85% of all Java projects
- Foundation of modern quality assurance — enables automated, repeatable, continuous testing

### Apache Log4j

- Approximately 76% of all Java applications use Log4j for logging — more than any other logging tool
- Critical component for logging, monitoring, and error analysis
- The Log4Shell vulnerability (December 2021) demonstrated the risks of unmanaged base dependencies — a critical security flaw that was latently present in millions of software stacks worldwide. More information: https://www.bsi.bund.de/dok/log4j

### Apache Maven

- Over 75% of all Java projects use Maven for build and project management
- Approximately 2 billion downloads per year
- Fundamental part of modern software development

### Apache Commons

- One of the central utility library collections in the Java ecosystem
- Approximately 49% of Java developers actively use Apache Commons
- Modular collection (Commons Lang, IO, Collections, etc.) providing proven, reusable standard functions

### Eclipse Temurin

- One of the leading OpenJDK distributions worldwide
- Over 500,000 downloads per day
- TCK-certified, AQAvit-verified, community-supported Java runtime
- Runtime foundation for countless enterprise Java applications

### What These Components Cover

Together, these components form the foundation of the entire technical trust chain of Java applications:

- **Build pipeline** — Apache Maven
- **Test strategy** — JUnit
- **Logging infrastructure** — Apache Log4j
- **Standard libraries** — Apache Commons
- **Java runtime** — Eclipse Temurin

## Where Support & Care Fits

Java applications can be structured into three vertical layers:

1. **Application code** — Business logic, developed in-house
2. **Frameworks** — Spring Boot, Quarkus, Jakarta EE, etc.
3. **Base components** — Runtime, build tools, logging, testing, utilities

The base components provide reusable infrastructure functionality but also carry the majority of technical risks: security vulnerabilities, transitive dependencies, and compliance responsibility. Support & Care targets this lowest layer directly — ensuring security, stability, and regulatory compliance at the foundation of the application.

Framework support alone is not sufficient. The Log4Shell vulnerability showed that a critical flaw in a widely-used base library can have enormous global impact, even when framework updates and vendor advisories exist — because they often reach affected applications too late or not at all.

## Services

### Long Term Support (LTS)

Continued support for the most important versions to better organize updates.

### Security Updates & Bugfixes

Timely information and notifications to ensure smooth and fast vulnerability remediation.

### Documentation & Transparency

Support with SBOM strategies and technical documentation — optionally provided in German or English.

### Workshops & Consulting

Direct exchange with maintainers and committers — available in German or English.

### Regular Webinars & Status Updates

Information on current security risks, important version changes, best-practice recommendations, and concrete impacts on the OSS supply chain.

### Custom Builds & Tooling

Tailored implementations directly from the maintainers.

### Hardened Containers for Government

Open Elements is an authorized provider for **container.gov.de** — the German federal container platform — alongside ZenDiS (Center for Digital Sovereignty of Public Administration) and the German Federal Foreign Office. The scope covers **hardened Eclipse Temurin container images** for the Java LTS versions **11, 17, 21, and 25+**.

The hardened images provide:

- Verified origin and quality assurance
- Up-to-date dependencies free of known vulnerabilities
- Software Bill of Materials (SBOM) per image
- Cryptographic signing
- Minimized attack surface through systematic hardening

This makes the offering directly usable for public-sector Java deployments with elevated security and sovereignty requirements.

## Business Model

Support & Care uses a transparent **cost-share model with strategic sponsorship**. This means:

- Customers share the ongoing maintenance and improvement costs for the supported open-source components — openly, transparently, and measurably
- **Funds flow directly to the maintainers** of the projects. Instead of adding superficial support layers, the investment goes into the vitality of each project's core
- Customer requirements and priorities are actively integrated into the project roadmaps, so development directly reflects real enterprise needs

### Public Funding

Sustainable maintenance of selected supported projects is co-funded by public programs. Open Elements receives funding from the **Sovereign Tech Agency** (a program of the German federal government / Sovereign Tech Fund) for the sustainable development of Apache Maven. 100% of these funds are used for Maven work in a transparent way, with a clearly disclosed share covering organisation and management.

### Proactive Communication

Customers are proactively kept informed about:

- Security warnings and new patches
- Planned API or major version changes
- Recommendations for version updates or dependency cleanups
- Trends and risks in the OSS ecosystem

## CRA Compliance

Open Elements acts as an **Open-Source Steward** with direct participation in developing best practices for regulatory compliance.

Through the founding membership in the **Open Regulatory Compliance Working Group (ORC WG)** of the Eclipse Foundation, Open Elements works together with leading open-source foundations, major technology companies, and EU representatives on concrete specifications, recommendations, and practical guidelines for implementing CRA requirements.

Support & Care helps with:

- Significantly reducing patch times
- Systematic vulnerability monitoring
- Making updates predictably available
- Ensuring documentation and transparency
- Guaranteeing long-term maintainability
- Prospective support for CRA-compliant attestations for supported projects — based on best practices developed in the ORC WG

See the `eclipse-info` skill (ORC WG section) for more details on the regulatory compliance work.

## Subscription Models

Support & Care offers three subscription tiers — **Basic**, **Standard**, and **Premium** — selectable based on requirements for availability, compliance, and SLA.

| | Basic | Standard | Premium |
|---|---|---|---|
| Included support hours per month¹ | 4 h | 8 h | 8 h |
| Response time² | 1 business day | 1 business day | 1 hour |
| Support channels³ | Helpdesk (DE/EN) | Helpdesk (DE/EN) | Helpdesk + Hotline (DE/EN) |
| Discount on additional support hours | 10 % | 15 % | 15 % |
| Quarterly webinar with experts⁴'⁵ | ✓ | ✓ | ✓ |
| Monthly newsletter | ✓ | ✓ | ✓ |
| Training discount | — | 10 % | 10 % |
| Individual monthly call with experts⁴'⁵ | — | ✓ | ✓ |

**Footnotes:**

1. Unused support hours expire at the end of the month — in that case the unused contribution directly supports further development of the relevant open-source components.
2. Business days are Monday–Friday, excluding public holidays in North Rhine-Westphalia (NRW), Germany.
3. The helpdesk uses a GDPR-compliant solution hosted in the EU. Each customer receives individual accounts; communication is available in German and English.
4. Experts are developers and technical staff who directly contribute to the respective OSS projects (e.g. as committers or maintainers).
5. Webinars and individual consultations are conducted via Zoom.

## Why Open Elements

Open Elements combines:

- **Community proximity** — Board seat at Eclipse Foundation, Technical Advisory Board at Linux Foundation
- **Enterprise experience** — Working with organizations on critical Java infrastructure
- **Regulatory know-how** — Active involvement in CRA compliance (ORC WG)
- **Sustainable OSS funding** — Revenue from Support & Care flows directly into the supported open-source projects
- **Transparency** — Open, traceable, and measurable contributions

Active contributions to critical OSS projects including Eclipse Adoptium, Jakarta EE, Apache Maven, and other key projects. See the `open-elements-info` skill for the full list of foundation memberships and roles.

### Maintainer vs. Vendor

A core distinction in Support & Care messaging: Open Elements is not a vendor reselling third-party OSS support — it is a **maintainer**. In the open-source world, a maintainer takes overall responsibility for a component: not only patching, but also further development, security posture, and the long-term roadmap.

For most foundational Java components there is hardly anyone who can or will take this responsibility. Open Elements can, because it has co-built and maintained these components for years — inside foundations, in technical committees, partially on a voluntary basis, long before a commercial market existed. **This depth cannot be bought; it has grown over years.** That is the structural difference between an OSS support provider and a maintainer, and it is the strongest argument for choosing Support & Care over generic vendor support contracts.

### Experts Behind Support & Care

A defining feature of Support & Care is that the people answering tickets are the same people who maintain the code. The named experts include:

- **Hendrik Ebbers** — Java Champion, Eclipse Foundation Board Member
- **Sandra Parsick** — Java Champion, OSS Maintainer
- **Sebastian Tiemann** — OSS Committer
- **Marc Philipp** — JUnit Team Lead, Java Champion

There is no downstream support team between customer and project — requests reach committers and maintainers directly.

## FAQ

The following canonical answers come directly from the Support & Care website and should be reused verbatim (or close to it) when answering equivalent questions in proposals, replies, or marketing copy.

**Is Support & Care only for Apache Maven?**
No. Support & Care covers five components: Eclipse Temurin, Apache Maven, JUnit, Apache Log4j, and Apache Commons. The offering started in 2024 with Maven and has been continuously expanded since.

**Who provides the support?**
Committers and maintainers of the respective open-source projects — the people who actually write and maintain the code. There is no downstream support team in between.

**What happens with the subscription fees?**
Fees flow transparently and traceably into the supported open-source projects: payment of maintainers, security updates, bugfixes, documentation, and infrastructure.

**Do I have to subscribe to all five components?**
No. Tailored scopes are possible — contact Open Elements to discuss specific component requirements.

**Does Support & Care help with CRA compliance?**
Yes. It addresses vulnerability monitoring, patch times, documentation, SBOM, long-term maintainability, and (prospectively) CRA-compliant attestations developed in the ORC WG.

**In which languages is support provided?**
German and English — for helpdesk requests as well as workshops, consulting, and documentation.

**How does Support & Care differ from framework support?**
Framework support (Spring Boot, Quarkus, Jakarta EE) covers the middle layer of the Java stack. Support & Care covers the foundational layer underneath: runtime, build tools, logging, testing, and utility libraries. The two are complementary, not interchangeable.

## Contact

- **Open Elements GmbH**
- Gerhart-Hauptmann-Str. 49B, 51379 Leverkusen, Germany
- Email: info@open-elements.com
- Phone: +49 151 22684622

## Key Links

| Resource | URL |
|----------|-----|
| Support & Care GitHub | https://github.com/support-and-care |
| Support & Care Landing Page | https://open-elements.com/support-care |
| Open Elements Website | https://open-elements.com |
| ORC WG | https://orcwg.org |
| BSI Log4Shell Info | https://www.bsi.bund.de/dok/log4j |
| Sovereign Tech Agency | https://www.sovereign.tech |
| container.gov.de | https://container.gov.de |

## Sources for Statistics

- JetBrains Developer Ecosystem Survey: https://www.jetbrains.com/lp/devecosystem-2021/java/
- New Relic State of the Java Ecosystem 2024: https://newrelic.com/de/resources/report/2024-state-of-the-java-ecosystem
