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
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.repo.GitRepositoryManager

/**
 * Shared rendering of the git branch label for a content root.
 *
 * Used both by [BranchDirectoryNode] (the Project view, via a replaced node) and by
 * [BranchNodeDecorator] (the Scopes view and other panes that go through the
 * [com.intellij.ide.projectView.ProjectViewNodeDecorator] extension point).
 *
 *   ⚡ main      on a default branch -> green
 *   ⚡ feature   on any other branch -> orange
 *   ⚡ main ↑    local commits to push (outgoing)
 *   ⚡ main ↓    remote commits to pull (incoming)
 *   ⚡ main ●    uncommitted local changes
 */
object BranchPresentation {

    private val DEFAULT_BRANCHES = setOf("main", "master")

    // JBColor(light, dark): adapts to the light/dark theme.
    private val MAIN_COLOR = JBColor(0x4C9A4C, 0x6FAF6F)   // green
    private val OTHER_COLOR = JBColor(0xC07A1E, 0xD9A23A)  // orange
    private val DIRTY_COLOR = JBColor(0xCC8800, 0xE0B040)  // amber (dirty working tree)

    /** True if [vf] is one of the project's content roots. */
    fun isContentRoot(project: Project, vf: VirtualFile): Boolean =
        vf in ProjectRootManager.getInstance(project).contentRoots

    /**
     * Replaces the path/location of the node represented by [vf] with the current git
     * branch (colored, with the status vs origin). Does nothing if [vf] is not inside a
     * git repository. [fallbackName] is used as the node text when the presentation has
     * not produced any text yet.
     */
    fun apply(project: Project, vf: VirtualFile, data: PresentationData, fallbackName: String?) {
        val repo = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(vf) ?: return

        // currentBranchName is null in detached HEAD / rebase: fall back to the SHA.
        val branch = repo.currentBranchName
        val label = branch ?: repo.currentRevision?.take(7)?.let { "($it)" } ?: return

        val arrows = buildString {
            if (branch != null) {
                val io = GitBranchIncomingOutgoingManager.getInstance(project)
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
        val clm = ChangeListManager.getInstance(project)
        val dirty = clm.allChanges.any { change ->
            underRoot(change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path)
        } || clm.unversionedFilesPaths.any { underRoot(it.path) }

        // Drop the path: show the branch as a colored fragment after the name.
        data.setLocationString("")
        if (data.coloredText.isEmpty()) {
            // No colored fragments yet: start again from the name.
            data.addText(data.presentableText ?: fallbackName ?: vf.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
        data.addText("  ⚡ $label$arrows", attrs)
        if (dirty) {
            // ● = uncommitted local changes
            data.addText(" ●", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, DIRTY_COLOR))
        }
    }
}
