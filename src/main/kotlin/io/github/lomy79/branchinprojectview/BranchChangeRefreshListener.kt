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

import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

/**
 * Forces a refresh of the Project tree (re-running the nodes' updateImpl) when:
 *  - a git repository's state changes (e.g. checking out another branch)
 *  - VCS mappings are discovered/updated (repos become available asynchronously
 *    after the project opens: without this, on startup the path would stay
 *    visible instead of the branch)
 *  - incoming/outgoing info vs origin changes (the ↑/↓ arrows)
 *  - the local change list changes (the ● dirty working tree marker)
 */
class BranchChangeRefreshListener(private val project: Project) :
    GitRepositoryChangeListener,
    VcsRepositoryMappingListener,
    GitIncomingOutgoingListener,
    ChangeListListener {

    override fun repositoryChanged(repository: GitRepository) = refreshProjectView()

    override fun mappingChanged() = refreshProjectView()

    override fun incomingOutgoingInfoChanged() = refreshProjectView()

    override fun changeListUpdateDone() = refreshProjectView()

    private fun refreshProjectView() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ProjectView.getInstance(project).currentProjectViewPane?.updateFromRoot(true)
        }
    }
}
