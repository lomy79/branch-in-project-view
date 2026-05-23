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

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.PsiDirectory
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.repo.GitRepositoryManager

/**
 * A PsiDirectoryNode variant that, for content roots, replaces the path
 * (location string) with the current git branch, colored by branch, plus the
 * status vs origin.
 *
 * We override updateImpl (the same path the node uses to produce the location):
 * unlike a ProjectViewNodeDecorator, these changes are actually rendered by the
 * 2026.x Project view.
 *
 *   ⚡ main      on a default branch -> green
 *   ⚡ feature   on any other branch -> orange
 *   ⚡ main ↑    local commits to push (outgoing)
 *   ⚡ main ↓    remote commits to pull (incoming)
 */
class BranchDirectoryNode(project: Project, directory: PsiDirectory, settings: ViewSettings?) :
    PsiDirectoryNode(project, directory, settings) {

    override fun updateImpl(data: PresentationData) {
        super.updateImpl(data)

        val proj = project ?: return
        val vf = virtualFile ?: return
        val repo = GitRepositoryManager.getInstance(proj).getRepositoryForFileQuick(vf) ?: return

        // currentBranchName is null in detached HEAD / rebase: fall back to the SHA.
        val branch = repo.currentBranchName
        val label = branch ?: repo.currentRevision?.take(7)?.let { "($it)" } ?: return

        val arrows = buildString {
            if (branch != null) {
                val io = GitBranchIncomingOutgoingManager.getInstance(proj)
                val ahead = io.hasOutgoingFor(repo, branch)
                val behind = io.hasIncomingFor(repo, branch)
                if (ahead || behind) append(' ')
                if (ahead) append('↑')   // to push
                if (behind) append('↓')  // to pull
            }
        }

        // Color by branch: default branch -> green, otherwise orange.
        val color = if (branch != null && branch.lowercase() in DEFAULT_BRANCHES) MAIN_COLOR else OTHER_COLOR
        val attrs = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color)

        // Is there anything uncommitted under this repo? (haveChangesUnder returns
        // UNSURE until the VCS scan completes, so we look at the changes instead.)
        // We consider both tracked changes and new untracked files.
        val rootPath = vf.path + "/"
        fun underRoot(p: String?) = p != null && (p == vf.path || p.startsWith(rootPath))
        val clm = ChangeListManager.getInstance(proj)
        val dirty = clm.allChanges.any { change ->
            underRoot(change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path)
        } || clm.unversionedFilesPaths.any { underRoot(it.path) }

        // Drop the path: show the branch as a colored fragment after the name.
        data.setLocationString("")
        if (data.coloredText.isEmpty()) {
            // super did not use colored fragments: start again from the name.
            data.addText(data.presentableText ?: name ?: vf.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
        data.addText("  ⚡ $label$arrows", attrs)
        if (dirty) {
            // ● = uncommitted local changes
            data.addText(" ●", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, DIRTY_COLOR))
        }
    }

    companion object {
        private val DEFAULT_BRANCHES = setOf("main", "master")

        // JBColor(light, dark): adapts to the light/dark theme.
        private val MAIN_COLOR = JBColor(0x4C9A4C, 0x6FAF6F)   // green
        private val OTHER_COLOR = JBColor(0xC07A1E, 0xD9A23A)  // orange
        private val DIRTY_COLOR = JBColor(0xCC8800, 0xE0B040)  // amber (dirty working tree)
    }
}
