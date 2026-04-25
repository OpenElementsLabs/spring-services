---
name: vision
license: Apache-2.0
metadata:
  source: https://github.com/open-elements/claude-base
  author: Open Elements
description: Clarify the vision and scope of a new application or planned features through structured product discovery. Conducts an interactive interview with the user, stress-tests their thinking with /grill-me, and produces a RESEARCH.md with user stories, personas, scope boundaries, and prioritized feature list. The RESEARCH.md serves as input for /spec-create or /spec-flow. Use this skill when the user wants to plan an app, define a product vision, brainstorm features, figure out what to build next, create a product roadmap, or says things like "I have an idea for an app", "what should we build", "let's plan the next features", or "help me define the scope".
---

# Vision — Product Discovery and Scope Definition

Turn a rough idea into a structured product research document through interactive discovery. The output is a `RESEARCH.md` that captures the what and why — user stories, personas, priorities, and scope — so that individual features can later be turned into specs via `/spec-create` or `/spec-flow`.

This skill focuses on product thinking, not technical design. The goal is to produce artifacts a project manager would create: who are the users, what do they need, what does success look like, and what is in or out of scope.

## Instructions

### 1. Understand the starting point

Determine what the user is working with:

- **New application** — The user has an idea but no code yet. Discovery covers the full product vision.
- **Existing application** — The user has a codebase and wants to plan the next set of features. Explore the existing project first to understand what already exists.

For existing applications, read key files (README, project structure, existing specs) to understand the current state before starting the conversation. This prevents asking questions the codebase already answers.

### 2. Set expectations about scope and effort

Before diving into discovery, have an honest conversation about scope. The size of the vision directly affects how long this session will take:

- **Focused scope** (a single feature or small feature group) — A shorter session, maybe 10–15 questions. Good for planning the next increment of an existing app.
- **Medium scope** (a major feature area or MVP of a small app) — A moderate session with 20–30 questions across several topics. Expect to spend some real time here.
- **Broad scope** (full application vision or product strategy) — A thorough session that will take considerable time. Many branches to explore, many questions to answer. Worth it for getting the foundation right, but the user should be prepared for the investment.

Ask the user: **"How broad is the scope you want to explore? This helps me calibrate — a single feature needs a focused session, while a full app vision means we'll be here for a while working through many questions."**

Agree on the scope level before continuing. If the user picks broad scope, acknowledge that this will be a substantial conversation and that thoroughness now saves confusion later.

### 3. Initial discovery

Start with open-ended questions to understand the big picture. Adapt based on whether this is a new app or an extension of an existing one.

**For a new application:**
- What problem does this application solve? Who has this problem today?
- How do people currently deal with this problem (workarounds, competitors, manual processes)?
- What is the one thing this application must do well to be worth building?
- Who are the different types of users? What are their roles and goals?

**For extending an existing application:**
- What feedback or pain points are driving these new features?
- Which users are asking for this? Are there users who would be negatively affected?
- How do the planned features relate to what already exists?

Do not ask all questions at once. Ask one or two, listen, then follow up based on the answers. The conversation should feel natural, not like filling out a form.

Build a mental map of the product as the user describes it. Track:
- **Users/personas** that emerge from the conversation
- **Features** the user mentions or implies
- **Constraints** (budget, timeline, team size, technical limitations)
- **Assumptions** the user is making (surface these explicitly)

### 4. Grill the vision

Once you have a reasonable understanding of the product idea, invoke `/grill-me` to stress-test the user's thinking. This is where hidden assumptions, missing requirements, and blind spots get surfaced.

Pass the gathered context to `/grill-me` as the topic. The grill session should focus on product and scope questions — not technical implementation details. Relevant branches include:

- **Problem validation** — Is this a real problem? How do you know? Who told you?
- **User understanding** — Do you actually know your users? Have you talked to them?
- **Scope boundaries** — What is explicitly out? Where does this product end and another begin?
- **Success criteria** — How will you know this worked? What metrics matter?
- **Prioritization** — If you can only ship three features, which three? Why those?
- **Risks** — What could make this fail? What are you most uncertain about?

After the grill session concludes, incorporate the resolved decisions and newly surfaced insights into the research document.

### 5. Synthesize user stories

Based on everything gathered, draft user stories for each feature or capability that emerged. Use the standard format:

```
As a [persona/role],
I want to [action/capability],
so that [benefit/value].
```

Group user stories by feature area or theme. Each story should be concrete enough that a developer could ask "what does done look like?" and get a clear answer from the story plus its acceptance criteria.

For each user story, add brief acceptance criteria — the conditions that must be true for the story to be considered complete. These are not technical test cases (those come later in specs), but user-visible outcomes.

Review the stories with the user. Ask: **"Do these stories capture what you described? Are any missing? Are any wrong?"**

### 6. Define scope and priorities

Work with the user to draw clear boundaries:

**In scope** — Features and capabilities that belong in this vision. Group them by priority:
- **Must have** — The product is not viable without these
- **Should have** — Important but the product could launch without them
- **Nice to have** — Valuable if time permits, but not essential

**Out of scope** — Things that were discussed but explicitly excluded. Recording what is out of scope is just as important as recording what is in — it prevents scope creep later.

**Open questions** — Unresolved items that need further research, user feedback, or technical investigation before they can be decided.

### 7. Write RESEARCH.md

Write the research document to `RESEARCH.md` in the project root (or in a location the user specifies). Use the following structure:

```markdown
# Research: <Product/Feature Name>

## Vision Statement

<One paragraph that captures what this product/feature set is, who it serves,
and why it matters. Someone reading only this paragraph should understand the
core intent.>

## Target Users

### <Persona Name>

- **Role:** <who they are>
- **Goal:** <what they are trying to achieve>
- **Pain point:** <what frustrates them today>
- **Context:** <relevant details about how/when/where they work>

(Repeat for each persona)

## User Stories

### <Feature Area / Theme>

#### <Story Title>

As a <persona>,
I want to <action>,
so that <benefit>.

**Acceptance criteria:**
- <criterion 1>
- <criterion 2>
- ...

(Repeat for each story, grouped by feature area)

## Scope

### In Scope

#### Must Have
- <feature/capability and brief description>

#### Should Have
- <feature/capability and brief description>

#### Nice to Have
- <feature/capability and brief description>

### Out of Scope
- <explicitly excluded item and why>

## Success Criteria

<How will you know this product/feature set is successful? Define measurable
or observable outcomes.>

- <criterion 1>
- <criterion 2>

## Constraints and Risks

### Constraints
- <budget, timeline, team, technology, regulatory, etc.>

### Risks
- <what could go wrong and potential mitigation>

## Open Questions

- <unresolved items that need further investigation>

## Next Steps

<Suggested order for turning this research into specs. Reference /spec-create
for individual feature specs or /spec-flow for end-to-end implementation.>
```

Not every section needs to be lengthy. A focused-scope research document might have two personas and five user stories. A broad-scope one might have six personas and thirty stories. Match the depth to the scope agreed in step 2.

### 8. Review and next steps

Present the completed `RESEARCH.md` to the user. Walk through the key sections and ask:

- Does the vision statement capture the essence of what you described?
- Are the personas accurate? Are any missing?
- Do the priorities feel right?
- Are there stories that should be split or combined?

After the user is satisfied, explain the next steps:

1. **Create individual specs** — Pick features from the "Must Have" list and run `/spec-create` for each one. The user stories and acceptance criteria in `RESEARCH.md` provide the starting point.
2. **Build a roadmap** — If the project uses a `ROADMAP.md`, the prioritized feature list maps directly to roadmap items.
3. **Implement end-to-end** — For features that already have specs, use `/spec-flow` to go from spec to Pull Request.

The research document is a living artifact — it can be updated as understanding deepens. But the specs created from it are the source of truth for implementation.

## Tone

Curious and collaborative. This is a brainstorming session, not an interrogation — that role belongs to `/grill-me` which gets invoked at the right moment. Ask genuine questions, reflect back what you hear, and help the user organize their thinking. Challenge assumptions gently during discovery, then let the grill session do the heavy lifting.
