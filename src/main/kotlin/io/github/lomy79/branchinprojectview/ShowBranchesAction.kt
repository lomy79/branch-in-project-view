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

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Action in the Project context menu: shows the branch checkout popup for the
 * repository of the selected module/file (see [BranchPopupFactory]). The same
 * logic is available by clicking the branch in the Project view (see
 * [BranchClickListener]).
 */
class ShowBranchesAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = repositoryFor(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repository = repositoryFor(e) ?: return
        BranchPopupFactory.create(project, repository)?.showInBestPositionFor(e.dataContext)
    }

    private fun repositoryFor(e: AnActionEvent): GitRepository? {
        val project = e.project ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file)
    }
}
