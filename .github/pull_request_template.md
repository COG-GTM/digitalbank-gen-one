<!--
Control ITGC-CM-07 (change management). Every change to `master` goes through a
pull request reviewed by someone other than the author. Incomplete templates
must not be approved. See docs/governance/branch-protection.md.
-->

## Change summary

**Jira:** <!-- e.g. https://cog-gtm.atlassian.net/browse/EM-000 (required) -->

<!-- What changed and why, in plain English. -->

## Change risk

**Risk level:** <!-- Low / Medium / High - pick one, see guide below -->

Areas touched (tick all that apply):

- [ ] Money movement (`**/service`, `**/controller`: accounts, transfers, deposits, withdrawals, credit, payments)
- [ ] Security (`**/security`, `**/config`, authentication, authorisation, secrets, TLS)
- [ ] Data model or database schema
- [ ] Customer personal data (PII)
- [ ] Build, deployment or infrastructure
- [ ] None of the above

Risk guide: **High** = money movement, security, schema or PII changes; **Medium** = other
customer-facing behaviour; **Low** = docs, tests or internal refactors with no behaviour change.

**Impact if this change is wrong:** <!-- who or what is affected -->

**Rollback plan:** <!-- how to revert safely; note any data migration -->

**Testing performed:** <!-- commands run and results; screenshots for UI changes -->

## Author attestation

- [ ] My commits are authored under my named corporate identity (no blank or machine-generated name/email) and are signed.
- [ ] This change contains no secrets, credentials or customer data.
- [ ] Tests covering this change pass locally.

## Reviewer attestation

To be completed by the approving reviewer (must not be the author). Approving
this PR confirms each statement below.

- [ ] I am not the author of, and did not co-author, the commits in this pull request.
- [ ] I have reviewed the full diff, including every file under a money-movement or security path.
- [ ] The change risk level above is accurate and the rollback plan is credible.
- [ ] The required status checks have passed on the latest commit.
- [ ] I understand that this approval is recorded as evidence of review under SM&CR.
