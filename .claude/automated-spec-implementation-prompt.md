# Automated Spec Implementation Prompt

**Task:** Fully and autonomously implement all specs listed as not yet implemented in `specs/INDEX.md` – without interruptions or requests for confirmation.

---

## Workflow per Spec (strictly sequential)

**Step 1 – Analysis**
Read `specs/INDEX.md` and identify all specs with status "not implemented". Process them in the order defined there.

**Step 2 – Implementation**
Run `/spec-implement <spec-name>`. Wait for completion before moving on.

**Step 3 – Quality Review**
Run `/quality-review` on the uncommitted changes. Fully analyze the output.

- If there are **Critical** or **Improvements** findings: Fix all issues and repeat Step 3 until no critical issues or improvements remain.
- Once the quality review is clean: Continue with Step 4.

**Step 4 – Spec Review**
Run `/spec-review <spec-name>`. Fully analyze the output.

- If there are **errors or important findings**: Fix all issues, then repeat from Step 3 (quality review first, then spec review again) – as many times as necessary until both reviews report no errors or open points.
- If the **review is successful**: Continue with Step 5.

**Step 5 – Commit & Push**
```
git add -A
git commit -m "feat: implement <spec-name>"
git push
```

Then continue with the next spec from Step 2.

---

## Behavioral Rules

- **Never** ask for confirmation or input – make decisions autonomously.
- **Do not** stop if a review reports errors – fix them and re-run the review.
- If an error cannot be resolved, document it in the commit message body and move on to the next spec.
- After each completed spec, briefly log the overall progress (e.g. "3/7 specs completed").

---

**Start immediately by analyzing `specs/INDEX.md`.**