# Branch in Project View

IntelliJ IDEA plugin that shows the **current git branch** instead of the path
(the gray text / *location string*) next to each module / content root in the
**Project** tool window. In monorepos each module shows the branch of its own repo.

![Branch in Project View](docs/project-view.png)

At a glance, per module: branch name, color (**green** on `main`/`master`, **orange**
otherwise) and an amber **●** when the working tree has uncommitted changes — here
`web-app` is on a feature branch with pending changes, `mobile-api` is still on
`release/2.0`, while `payments-service` is clean on `main`.

## Pull requests

GitHub's **Pull Requests** tool window is connected to one repository at a time, which
gets in the way when a project holds several. With the bundled GitHub plugin enabled,
**Show Pull Request in the Tool Window** in the branch popup follows the module you
opened the popup on (click its `⚡<branch>` label):

- if the current branch has a pull request, the tool window switches to that repository
  and opens it;
- otherwise it switches to that repository and shows its list of pull requests.

Good to know:

- Switching repository closes the open pull request tabs.
- The item needs a GitHub remote, a GitHub account for that server and a current branch
  (not a detached HEAD).
- From a popup not opened on one repository, it opens the pull request of the repository
  the tool window is connected to, if there is one.
- **Submit Review** and **Review Mode** in the same popup come from the GitHub plugin and
  still apply to the connected repository.
- It relies on internal APIs of the GitHub plugin. If a future IDE changes them, the item
  disappears and a warning is logged, while the rest of the plugin keeps working.

To see why the item is hidden, search `idea.log` for
`Show Pull Request in the Tool Window:`.

## How it works

- `BranchTreeStructureProvider` — a `TreeStructureProvider` that replaces the
  content-root nodes (the top-level "modules") with `BranchDirectoryNode`.
- `BranchDirectoryNode` — a `PsiDirectoryNode` subclass that overrides
  `updateImpl` to show, instead of the path:
  - a colored `⚡ <branch>`: **green** on `main`/`master`, **orange** on other branches;
  - `↑` (commits to push) / `↓` (commits to pull) arrows vs origin;
  - an **amber ●** if the repo's working tree has uncommitted changes.
  In detached HEAD it shows the short SHA.
- `ShowBranchesAction` + `BranchClickListener` — open the **native Git branch
  popup** (the same as "Git → Branches", with search) targeted at the module's
  repo, both from the context menu ("Git Branches…") and by **clicking the
  `⚡<branch>` text** on the node. The native popup (`GitBranchesTreePopupOnBackend`)
  is invoked via reflection because it is `internal` in Kotlin; falls back to a
  simple popup if unavailable.
- `BranchChangeRefreshListener` — forces a tree refresh on repo change, repo
  discovery (VCS mapping, async at startup) and incoming/outgoing info changes.
- `ShowPullRequestInRepositoryAction` + `PullRequestRepositorySwitcher` — override
  GitHub's **Show Pull Request in the Tool Window** in the branch popup, which only
  knows the connected repository, so it works for every GitHub repository. It connects the **Pull Requests** tool window to that repository and opens the pull
  request of the current branch, or the repository's list of pull requests if there is
  none. Changing repository closes open pull request tabs, since the tool window holds
  one connection at a time. Needs a GitHub account for the repository's server. Only
  active if the bundled GitHub plugin is enabled.

> Note: on IntelliJ 2026.x the classic `ProjectViewNodeDecorator` does **not**
> affect the rendering of Project view nodes (new architecture). That's why we act
> on the node itself via `TreeStructureProvider`, whose presentation is rendered.

## Build

Requires a JDK 21 (picked up by the toolchain) and uses the local IDE as the SDK
(`/home/andrea/work/idea-IU-262.5752.32` unless overridden with `-PlocalIdePath=...`).

```bash
./gradlew buildPlugin
# artifact: build/distributions/branch-in-project-view-<version>.zip
```

## Installation

In IntelliJ: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and select
the zip in `build/distributions/`. Then restart the IDE.

## Development / quick test

```bash
./gradlew runIde   # launches a sandbox IDE instance with the plugin installed
```

## License

Licensed under the [Apache License 2.0](LICENSE). Copyright 2026 Andrea Aresu.
