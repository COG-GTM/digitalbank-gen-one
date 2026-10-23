# Change management controls for `master` (ITGC-CM-07)

Jira: [EM-137](https://cog-gtm.atlassian.net/browse/EM-137) (audit finding FND-07).

The audit of `master` at `59d94a6` found 224 commits pushed directly with no pull-request
review, no CODEOWNERS file, 28 commits with a blank author, 90 commits from the
machine-style identity `ma601572` with host-derived e-mails, and no commit with a verified
signature. That breaks FCA SYSC 4.1.1R, PRA Fundamental Rule 6 and the SM&CR duty of
responsibility (FSMA 2000 s.66A(5)): the bank cannot show that a second person reviewed a
change, or prove who made it.

Controls are split between what lives in the repository and what only a GitHub admin can switch on.

## 1. Controls in this repository

| Control | File | Enforced by |
| --- | --- | --- |
| Named owners for money-movement and security code | `.github/CODEOWNERS` | GitHub, once "Require review from Code Owners" is enabled (section 2) |
| Change-risk statement and reviewer attestation on every PR | `.github/pull_request_template.md` | Reviewers; un-completed templates must not be approved |
| CODEOWNERS / template / this document cannot silently regress | `bank/src/test/java/io/digisic/bank/test/junit/governance/GovernanceControlsTest.java`, run by `scripts/governance/check-codeowners.sh` and the `governance-checks` workflow | Required status check (section 2) |

Owner teams used in CODEOWNERS:

| Team | Owns |
| --- | --- |
| `@COG-GTM/digitalbank-maintainers` | Everything (default rule) |
| `@COG-GTM/digitalbank-payments-owners` | `**/service/` and `**/controller/` under `bank/` and `credit/` `src/main/java` |
| `@COG-GTM/digitalbank-security-owners` | `**/security/`, `**/config/`, `src/main/resources/*.properties`, `.github/`, `docs/governance/`, `scripts/governance/` |

Run the check locally before changing CODEOWNERS:

```bash
scripts/governance/check-codeowners.sh
```

## 2. Admin-only actions (GitHub settings)

These cannot be set from code. An organisation or repository admin must apply them and record
the change (screenshot or audit-log export) as evidence against EM-137.

### 2.1 Create the owner teams

Create `digitalbank-maintainers`, `digitalbank-payments-owners` and `digitalbank-security-owners`
in the `COG-GTM` organisation, give each **Write** access to `COG-GTM/digitalbank-gen-one`, and
add at least two named people to each (so an author can never be the only available code owner).
Until the teams exist GitHub marks the CODEOWNERS rules as invalid and they do not request reviews.

### 2.2 Branch protection for `master`

Settings → Branches → Branch protection rules → `master` (or an equivalent repository ruleset
targeting the default branch). Enable exactly:

- [ ] **Require a pull request before merging**
  - [ ] **Require approvals**: 1 (minimum; 2 recommended for money-movement changes)
  - [ ] **Dismiss stale pull request approvals when new commits are pushed**
  - [ ] **Require review from Code Owners**
  - [ ] **Require approval of the most recent reviewable push** (stops an author approving their own last push)
- [ ] **Require status checks to pass before merging**
  - [ ] **Require branches to be up to date before merging**
  - [ ] Required check: `CODEOWNERS coverage (ITGC-CM-07)` (from `.github/workflows/governance-checks.yml`), plus the build/test checks added by the CI pipeline (EM-138) once they exist
- [ ] **Require conversation resolution before merging**
- [ ] **Require signed commits**
- [ ] **Require linear history**
- [ ] **Do not allow bypassing the above settings** (applies the rules to admins too)
- [ ] **Allow force pushes**: off
- [ ] **Allow deletions**: off

GitHub never lets a PR author approve their own PR; together with "Require approval of the most
recent reviewable push" this gives the "no self-approval" control.

The same settings through the API (admin token required):

```bash
gh api -X PUT repos/COG-GTM/digitalbank-gen-one/branches/master/protection \
  -H "Accept: application/vnd.github+json" --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["CODEOWNERS coverage (ITGC-CM-07)"] },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": true,
    "require_last_push_approval": true
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON

gh api -X POST repos/COG-GTM/digitalbank-gen-one/branches/master/protection/required_signatures
```

Verify with `gh api repos/COG-GTM/digitalbank-gen-one/branches/master/protection` (the audit
token received HTTP 403 on this endpoint, so the auditor needs read access to it).

### 2.3 Named corporate identities

- Enforce SSO (SAML) for the `COG-GTM` organisation so every committer maps to a named employee.
- Require each developer to set `user.name` to their full name and `user.email` to their
  verified corporate address, and to sign commits (GPG, SSH or S/MIME key registered on
  their GitHub account):

  ```bash
  git config --global user.name "First Last"
  git config --global user.email "first.last@<corporate-domain>"
  git config --global gpg.format ssh
  git config --global user.signingkey ~/.ssh/id_ed25519.pub
  git config --global commit.gpgsign true
  ```

- Enable **Settings → Code security → Push protection** and, if available on the plan, a
  ruleset with **Restrict commit metadata** requiring author e-mails on the corporate domain.

### 2.4 Historical commits

History on `master` is not rewritten. The 28 blank-author commits and 90 `ma601572` commits
should be attributed to named individuals in a separate record held by the change-management
owner (commit SHA → person, from HR/IT records), so the bank can evidence who made each change.
