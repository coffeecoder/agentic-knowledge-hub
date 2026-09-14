# Git and GitHub Learning Handbook

Run repository commands from the Agentic Knowledge Hub checkout root. Commands below are reference examples; commit, push, and pull-request steps are actions to run only when you are ready to publish the reviewed work.

## 1. Core concepts

| Term | Meaning |
|---|---|
| Git | Distributed version control; local history and commits work without GitHub. |
| GitHub | Hosts Git repositories and adds pull requests, reviews, issues, and Actions. |
| Working tree | Files currently checked out and edited on disk. |
| Index / staging area | The exact changes selected for the next commit. |
| Commit | A snapshot with metadata and parent references, identified by a hash. |
| Branch | A movable reference to a commit, advanced by new commits. |
| HEAD | Usually points to the current branch; can point directly to a commit. |
| Remote | A named repository URL; this checkout uses `origin`. |
| Remote-tracking branch | Local record such as `origin/main`, refreshed by fetch. |
| Upstream | Branch used by default for tracking, pull, and some push operations. |
| Pull request (PR) | A GitHub proposal to review and merge one branch into another. |

Changes flow from working tree to staging area with `add`, into local history with `commit`, and to a remote with `push`. A commit includes staged content, not every edit on disk.

## 2. Configuration and GitHub CLI authentication

Check installed tools and configure your commit identity. Replace the sample identity with your own verified or GitHub-provided noreply email:

```bash
git --version
gh --version
git config --global user.name "Your Name"
git config --global user.email "YOUR_EMAIL"
git config --get user.name
git config --get user.email
```

Omit `--global` when setting identity only for this repository. Commit identity is attribution; it does not authenticate you to GitHub.

For this checkout's HTTPS remote, authenticate in the browser and configure Git's credential helper:

```bash
gh auth login --hostname github.com --git-protocol https --web
gh auth setup-git
gh auth status
git remote -v
```

The current `origin` is `https://github.com/coffeecoder/agentic-knowledge-hub.git`. Inspect it before publishing. GitHub CLI can configure Git credentials through its [authentication setup](https://cli.github.com/manual/gh_auth_login) and [Git credential helper](https://cli.github.com/manual/gh_auth_setup-git).

Never paste tokens into repository files, remote URLs, or shell commands. Prefer the browser flow and OS credential storage. GitHub CLI may fall back to a plaintext credential file if a secure store is unavailable; check its output. If your team uses SSH, use its approved SSH-key setup and an SSH remote instead. Organization access may additionally require SSO authorization.

## 3. Everyday commands

| Command | Purpose |
|---|---|
| `git status --short` | Show staged, unstaged, and untracked files. |
| `git diff` | Review unstaged changes to tracked files. |
| `git diff --cached` | Review the staged changes that will be committed. |
| `git add docs/learning/git_github_info.md` | Stage one reviewed file. |
| `git add -p` | Select individual change hunks interactively. |
| `git commit -m "docs: add Git learning handbook"` | Record staged changes locally. |
| `git fetch origin` | Download history and update remote-tracking refs without integrating it into your branch. |
| `git pull --ff-only` | Fetch and advance the current branch only if no merge or rebase is needed. |
| `git push` | Publish to the configured upstream when push configuration permits. |
| `git log --oneline --graph --decorate -15` | Inspect recent branch history. |

Plain `git pull` fetches and integrates according to configuration. `--ff-only` makes divergence explicit rather than silently creating a merge. Fetching does not make an existing `origin/main` permanently current; another person can push afterward.

Prefer explicit paths or `add -p` over `git add .` when unrelated files or sensitive material may be present. New untracked files do not appear in plain `git diff`; inspect them directly before staging.

## 4. Branch and pull-request workflow

Start with a clean working tree, or save unfinished changes using the stash section. The repository uses `main`; use `codex/` for new agent-created branches.

```bash
git status
git switch main
git fetch origin
git pull --ff-only
git switch -c codex/learning-handbook
```

Make the intended changes, then inspect and validate. These are the repository's required pre-commit checks from [AGENTS.md](../../AGENTS.md):

```bash
./mvnw verify
git diff --check
```

Use JDK 21 and the checked-in Maven wrapper. Run `./mvnw spotless:apply` before verification when Java formatting needs updating. Behavior changes require tests. Documentation-only edits should also have their links, commands, and Markdown reviewed. The current [CI workflow](../../.github/workflows/ci.yml) runs Maven verification (compilation, JUnit tests, JAR packaging, and formatting checks) on pull requests and pushes to `main` or `develop`.

When ready to commit and publish, stage only the intended files and review the staging area:

```bash
git add README.md docs/learning/README.md docs/learning/docker_info.md docs/learning/git_github_info.md
git diff --cached
git diff --cached --check
git commit -m "docs: add structured learning handbook"
git push -u origin codex/learning-handbook
gh pr create --base main --head codex/learning-handbook --title "docs: add structured learning handbook" --body "Add project-specific Docker and Git learning notes and navigation links."
gh pr checks
gh pr view --web
```

The first push's `-u` sets the upstream. Include actual validation results in the PR description. Address review feedback with additional commits and pushes, wait for required checks and approval, then merge according to repository policy. See the [GitHub CLI PR creation reference](https://cli.github.com/manual/gh_pr_create).

After a PR is merged, return to `main`, pull with `--ff-only`, and optionally delete the local feature branch with `git branch -d codex/learning-handbook`. If deletion refuses after a squash merge, inspect the result; do not blindly force deletion.

## 5. Safe undo and stash

Review `git status` and the relevant diff first. Substitute a real file path or commit hash for uppercase placeholders below.

| Intent | Command | Effect / caution |
|---|---|---|
| Unstage a file | `git restore --staged PATH_TO_FILE` | Keeps the working-tree edits. |
| Discard unstaged tracked-file edits | `git restore -- PATH_TO_FILE` | Replaces working content with the index version; discarded edits may be unrecoverable. Save a copy first if uncertain. |
| Undo a published ordinary commit | `git revert COMMIT_HASH` | Creates a new inverse commit, preserving shared history. Merge commits need additional parent-selection care. |
| Fix the most recent unpublished commit | `git commit --amend` | Rewrites that commit; review staged changes first and avoid on shared history. |
| Find a previous local branch position | `git reflog` | Helps locate commits after resets or rebases; not a backup of arbitrary uncommitted files. |

Prefer revert for shared commits. Avoid `git reset --hard`, `git clean -fd`, and force pushes as routine fixes: they can discard work or rewrite shared history. The [Git manual](https://git-scm.com/docs/user-manual) explains history inspection and recovery.

Temporarily save tracked edits and untracked files:

```bash
git stash push -u -m "WIP before switching branches"
git stash list
git stash show -p 'stash@{0}'
git stash apply 'stash@{0}'
```

`-u` includes untracked files but excludes ignored files. `apply` retains the stash. After verifying the restored work, remove that saved entry with `git stash drop 'stash@{0}'`. `pop` applies and removes it on success; conflicts leave it available. Resolve conflict markers and inspect the result before dropping anything. A stash is local and is not uploaded by normal pushes. Avoid stashing sensitive untracked documents; stash data lives in Git's object database. See [git-stash](https://git-scm.com/docs/git-stash).

## 6. .gitignore and security

The current [.gitignore](../../.gitignore) excludes `.env`, `.venv/`, historical Python caches and coverage output, Java/Maven `target/` and class files, IDE settings, build output, and `.DS_Store`. It does not automatically exclude every credential file, `.env` variant, or confidential document.

```bash
git check-ignore -v .env
git status --short --untracked-files=all
git diff --cached
```

Ignore rules affect untracked files, not files already committed. If an ordinary local-only file was mistakenly tracked, `git rm --cached PATH_TO_FILE` stages its removal from version control while keeping the local copy; add an appropriate ignore rule and review the staged deletion. This does not remove past copies from history. See [gitignore](https://git-scm.com/docs/gitignore).

Never commit `.env`, service-account keys, authorization tokens, embeddings, or client-confidential documents. Use only synthetic or public content in this portfolio; follow the [root security guidance](../../README.md#security). Keep example configuration free of real secrets. Review diffs and diagnostics before sharing them.

If a secret was committed, revoke or rotate it promptly. Removing the current file is insufficient because history retains it; coordinate any history cleanup with maintainers. Do not publish the secret in an issue or PR while reporting the incident.

## 7. Common errors and troubleshooting

| Error / symptom | Diagnose and recover |
|---|---|
| `not a git repository` | Check `pwd` and change to the checkout root. Do not run `git init` merely to silence it. |
| `command not found: gh` or `git` | Install the missing tool from its official distribution and restart a terminal with the correct `PATH`. |
| `Author identity unknown` | Configure `user.name` and `user.email`; authentication alone does not set commit identity. |
| Authentication failed / repository not found | Inspect `git remote -v` and `gh auth status`; check the active account, repository permission, and organization SSO. Re-run the browser login if needed. |
| `Permission denied (publickey)` | The remote uses SSH. Check the loaded key and GitHub account, or deliberately choose HTTPS and configure the CLI credential helper. |
| Current branch has no upstream | Inspect `git branch --show-current`, then use `git push -u origin BRANCH_NAME` for the intended branch. |
| Push rejected / non-fast-forward | Fetch and inspect `git log --oneline --graph --all -20`. Integrate remote work using the team's merge/rebase policy, validate, then push. Do not immediately force push. |
| Pull refuses divergent branches | `--ff-only` cannot integrate two independent lines of commits. Fetch, inspect both, and deliberately merge or rebase. |
| Local changes would be overwritten | Commit reviewed work or stash it before switching or pulling; do not discard it to clear the error. |
| Protected branch push rejected | Push a feature branch and open a PR; satisfy checks and reviews. |
| Nothing to commit | Check that files were saved, staged, and not ignored; use `git check-ignore -v PATH_TO_FILE` for an ignored path. |
| PR has failing checks | Inspect `gh pr checks` and the Actions logs, reproduce the failing check locally, fix, and push another commit. |

For a merge conflict, use `git status` to find affected files, edit conflict markers into the intended combined content, stage resolved files, and run validation. Finish a merge with `git merge --continue`, or a rebase with `git rebase --continue`, according to the active operation. To abandon the operation, use `git merge --abort` or `git rebase --abort`; preserve valuable edits beforehand. Avoid rebasing shared commits without coordination.

## 8. Interview notes

- **Git versus GitHub:** Git manages distributed history; GitHub hosts repositories and collaboration features.
- **Fetch versus pull:** Fetch updates downloaded history and remote-tracking refs; pull also integrates into the current branch.
- **Commit versus push:** A commit is local; a push updates remote refs and transfers required objects.
- **Merge versus rebase:** Merge combines histories, sometimes with a merge commit. Rebase replays commits onto a new base, producing new identities.
- **Reset versus revert versus restore:** Reset moves a branch and can change index/working files depending on mode; revert records an inverse commit; restore changes file content or staging state.
- **Conflict:** Git cannot automatically reconcile changes; a person must choose the correct resulting content and validate it.
- **Detached HEAD:** HEAD points directly to a commit. Create a branch to retain new work before switching away.
- **Cherry-pick:** Applies a selected commit's change as a new commit on the current branch; useful for a focused fix but may conflict.
- **.gitignore:** Prevents accidental addition of matching untracked paths; it is neither access control nor history sanitization.
- **PR versus merge:** A PR is a review proposal on GitHub; merge is a Git history operation. Checks and reviews complement each other.

Return to the [learning index](README.md) or the [Docker reference](docker_info.md).
