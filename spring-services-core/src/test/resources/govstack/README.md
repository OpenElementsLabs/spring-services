# Vendored GovStack common API components

This directory holds one file that is **not** written by this project:
`govstack-openapi-common.yaml`, the GovStack common OpenAPI schemas for problem details, field
validation and cursor pagination. It is used by `GovStackContractTest` to validate that every error
response this library produces is acceptable to a GovStack building block.

## Provenance

| Field | Value |
| --- | --- |
| Source | <https://github.com/jeremi/govstack-api-common>, `openapi/govstack-openapi-common.yaml` |
| Version | `0.1.0-draft` (unratified incubation artifact) |
| Commit | `12b9b1acde9bfaa5a77e26380fa8f9e1f29bb28e` |
| Branch | `agent/close-component-lint-gaps` — **not** on `main` |
| SHA-256 | `05d1bfc89c86a8d64005e343268326b3fb43cd044fe30f662fec0f9f573b0c73` |
| Licence | Apache-2.0 |

`GovStackContractTest` verifies the SHA-256 before it runs. The version string alone does not
identify this file: `main` carries a *different* `0.1.0-draft` that requires `code` and `timestamp`
members, which contradicts API Design Guide §11.2/§11.4. Pinning the hash is what makes the contract
test mean something.

The branch not being merged is tracked in `docs/TODO.md` ("Ask the GovStack common-components
maintainer to merge the pinned schema"): a force-push or a deleted branch would make the provenance
unverifiable, though the test would keep working on the vendored copy.

## Why it is vendored at all

The normative source is the GovStack API Design Guide, chapter 11. This artifact is an optional
schema companion (§2.8), useful because it turns the Guide's prose into something a test can execute.
It is test scope only and is never shipped.

## Licence notice

```
Copyright the GovStack API Working Group

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
