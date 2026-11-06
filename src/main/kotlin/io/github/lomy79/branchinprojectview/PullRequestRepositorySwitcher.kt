/*
 * Copyright 2026 Andrea Aresu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.lomy79.branchinprojectview

import com.intellij.collaboration.util.getOrNull
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.actions.branch.GitBranchActionsUtil
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import kotlin.time.Duration.Companion.seconds

private const val PREFERRED_REMOTE = "origin"
private const val NOTIFICATION_GROUP = "Branch in Project View"
private val LOADING_TIMEOUT = 30.seconds

private val LOG = logger<PullRequestRepositorySwitcher>()

/**
 * "Show Pull Request in the Tool Window" for any GitHub repository of the project: opens
 * the pull request of the current branch, or else the repository's list of pull requests.
 *
 * Overrides the GitHub plugin's action with the same text, which only knows the connected
 * repository: it stays visible, and opens that repository's pull request, in the branch
 * popup of any other repository.
 */
internal class ShowPullRequestInRepositoryAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val switcher = project.service<PullRequestRepositorySwitcher>()
        val repository = repositoryOf(e)
        switcher.lastUpdatedRepository = repository
        val affected = GitBranchActionsUtil.getAffectedRepositories(e).size
        e.presentation.isEnabledAndVisible = switcher.canSwitchTo(repository, "place=${e.place}, affected=$affected")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val switcher = project.service<PullRequestRepositorySwitcher>()
        // The popup has already closed by now, which forgets the repository it was opened on.
        val repository = repositoryOf(e) ?: switcher.lastUpdatedRepository
        if (repository != null) switcher.showCurrentBranchPullRequest(repository) else switcher.showConnectedPullRequest()
    }

    /**
     * The repository the popup was opened on. The affected repositories are not enough:
     * they are all the roots when Git is set to run branch operations on all of them.
     */
    private fun repositoryOf(e: AnActionEvent): GitRepository? =
        e.getData(GitBranchActionsDataKeys.SELECTED_REPOSITORY)
            ?: BranchPopupFactory.openedRepository?.takeIf { it.project == e.project }
            ?: GitBranchActionsUtil.getAffectedRepositories(e).singleOrNull()
}

/**
 * Starts tracking the known GitHub repositories: the state is shared lazily and stays empty
 * until its first subscriber, which would keep the action hidden.
 */
internal class PullRequestRepositoriesPreloadActivity : ProjectActivity {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun execute(project: Project) {
        project.service<GHHostedRepositoriesManager>().knownRepositoriesState.first()
    }
}

/**
 * Connects the GitHub "Pull Requests" tool window to a repository and shows the pull
 * request of its current branch, or else its list of pull requests. The tool window only
 * holds one connection at a time, so this closes the tabs of the previous repository.
 */
@Service(Service.Level.PROJECT)
internal class PullRequestRepositorySwitcher(
    private val project: Project,
    private val scope: CoroutineScope,
) {
    @Volatile
    var lastUpdatedRepository: GitRepository? = null

    @Volatile
    private var lastLoggedDecision: String? = null

    /** The GitHub plugin only offers internal APIs here: on a future incompatible IDE, hide the action. */
    fun canSwitchTo(repository: GitRepository?, context: String): Boolean = try {
        val hiddenReason = hiddenReason(repository)
        logDecision("${repository?.root?.name}: ${hiddenReason?.let { "hidden, $it" } ?: "visible"} ($context)")
        hiddenReason == null
    }
    catch (e: LinkageError) {
        LOG.warn("GitHub pull request API is not compatible, cannot switch repository", e)
        false
    }

    private fun hiddenReason(repository: GitRepository?): String? {
        if (repository == null) {
            // Not opened on one repository, e.g. the main toolbar: what GitHub's action did.
            return if (connectedPullRequest() != null) null else "popup gave no single repository"
        }
        val mapping = pickMapping(repository) ?: return "no GitHub remote known for this repository"
        return when {
            repository.currentBranchName == null -> "no current branch"
            pickAccount(mapping) == null -> "no GitHub account for ${mapping.repository.serverPath}"
            else -> null
        }
    }

    private fun connectedPullRequest(): GHPRIdentifier? =
        project.service<GHPRProjectViewModel>().connectedProjectVm.value?.prOnCurrentBranch?.value?.getOrNull()

    fun showConnectedPullRequest() {
        val projectVm = project.service<GHPRProjectViewModel>()
        val pullRequest = connectedPullRequest() ?: return
        projectVm.activate()
        projectVm.connectedProjectVm.value?.viewPullRequest(pullRequest, true)
    }

    /** Actions update often: only log when the decision changes. */
    private fun logDecision(decision: String) {
        if (decision == lastLoggedDecision) return
        lastLoggedDecision = decision
        LOG.info("Show Pull Request in the Tool Window: $decision")
    }

    fun showCurrentBranchPullRequest(repository: GitRepository) {
        val mapping = pickMapping(repository) ?: return
        val account = pickAccount(mapping) ?: return
        val projectVm = project.service<GHPRProjectViewModel>()

        // Opens the tool window and connects it. Stays silent if the connection fails, hence the timeout below.
        projectVm.activateAndAwaitProject(mapping.repository to account) {}

        scope.launch {
            val connected = withTimeoutOrNull(LOADING_TIMEOUT) {
                projectVm.connectedProjectVm.filterNotNull().first { it.repository == mapping.repository }
            }
            if (connected == null) {
                notifyNotConnected(repository)
                return@launch
            }
            val pullRequest = withTimeoutOrNull(LOADING_TIMEOUT) {
                // Null until it has been computed for the first time.
                connected.prOnCurrentBranch.filterNotNull().first { !it.isInProgress }.getOrNull()
            }
            if (pullRequest != null) connected.viewPullRequest(pullRequest, true) else connected.viewList(true)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pickMapping(repository: GitRepository): GHGitRepositoryMapping? {
        val candidates = project.service<GHHostedRepositoriesManager>().knownRepositoriesState.value
            .filter { it.gitRepository == repository }
        return candidates.firstOrNull { it.gitRemote.name == PREFERRED_REMOTE } ?: candidates.firstOrNull()
    }

    private fun pickAccount(mapping: GHGitRepositoryMapping): GithubAccount? {
        val server = mapping.repository.serverPath
        return ApplicationManager.getApplication().service<GHAccountManager>().accountsState.value
            .firstOrNull { it.server.equals(server, true) }
    }

    private fun notifyNotConnected(repository: GitRepository) {
        val content = "Could not connect the Pull Requests tool window to this repository in time."
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(repository.root.name, content, NotificationType.INFORMATION)
            .notify(project)
    }
}
