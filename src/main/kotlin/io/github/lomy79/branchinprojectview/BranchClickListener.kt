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

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewListener
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.tree.TreeUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * Attaches a [BranchClickListener] to the tree of every Project tool window pane
 * (Project, Scopes, …) so the branch fragment is clickable wherever it is shown.
 *
 * The currently visible pane is hooked as soon as it is available after the project
 * opens; every pane shown later is hooked via [ProjectViewListener.paneShown].
 */
class BranchClickActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // The pane that is already visible at startup.
        attachWhenReady(project, attempt = 0)

        // Any pane shown later (e.g. switching to the Scopes view) gets its tree hooked too.
        project.messageBus.connect().subscribe(
            ProjectViewListener.TOPIC,
            object : ProjectViewListener {
                override fun paneShown(current: AbstractProjectViewPane, previous: AbstractProjectViewPane?) {
                    attach(project, current.tree)
                }
            },
        )
    }

    private fun attachWhenReady(project: Project, attempt: Int) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val tree = ProjectView.getInstance(project).currentProjectViewPane?.tree
            when {
                tree != null -> attach(project, tree)
                attempt < 40 -> AppExecutorUtil.getAppScheduledExecutorService()
                    .schedule({ attachWhenReady(project, attempt + 1) }, 500, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun attach(project: Project, tree: JTree?) {
        if (tree == null || tree.getClientProperty(ATTACHED) == true) return
        tree.putClientProperty(ATTACHED, true)
        tree.addMouseListener(BranchClickListener(project, tree))
    }

    private companion object {
        const val ATTACHED = "branchview.click.attached"
    }
}

/**
 * Opens the checkout popup when the branch fragment (⚡ … / ●) of a content-root
 * node is clicked. Clicking the module name is left unchanged.
 */
private class BranchClickListener(private val project: Project, private val tree: JTree) : MouseAdapter() {

    override fun mouseClicked(e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1 || e.clickCount != 1) return

        val row = tree.getRowForLocation(e.x, e.y)
        if (row < 0) return
        val path = tree.getPathForRow(row) ?: return

        // Resolving a node's virtual file may touch the workspace model (e.g. the Scopes
        // view), which requires a read action even on the EDT.
        val repo = ReadAction.compute<GitRepository?, RuntimeException> {
            val vf = contentRootAt(path) ?: return@compute null
            GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(vf)
        } ?: return

        if (!clickedOnBranchFragment(e, path, row)) return

        BranchPopupFactory.create(project, repo)?.show(RelativePoint(e))
        e.consume()
    }

    /** true if the click lands on the fragment containing ⚡ or ● (the branch text). */
    private fun clickedOnBranchFragment(e: MouseEvent, path: TreePath, row: Int): Boolean {
        val bounds = tree.getPathBounds(path) ?: return false
        val comp = tree.cellRenderer.getTreeCellRendererComponent(
            tree, path.lastPathComponent, tree.isRowSelected(row),
            tree.isExpanded(row), /* leaf = */ false, row, /* hasFocus = */ false
        ) as? SimpleColoredComponent ?: return false
        comp.size = comp.preferredSize // ensure the fragments are laid out

        val index = comp.findFragmentAt(e.x - bounds.x)
        if (index < 0) return false
        val text = fragmentText(comp, index) ?: return false
        return text.contains('⚡') || text.contains('●')
    }

    private fun fragmentText(comp: SimpleColoredComponent, index: Int): String? {
        val it = comp.iterator()
        var i = 0
        while (it.hasNext()) {
            it.next()
            if (i == index) return it.fragment
            i++
        }
        return null
    }

    /**
     * The content root represented by [path], or null if the node is not a content root.
     * Works for any pane: the Project view ([BranchDirectoryNode]) and the Scopes view
     * (private [ProjectViewNode] subtypes) alike.
     */
    private fun contentRootAt(path: TreePath): VirtualFile? {
        val uo = TreeUtil.getLastUserObject(path) ?: return null
        val node = uo as? ProjectViewNode<*>
            ?: (uo as? NodeDescriptor<*>)?.element as? ProjectViewNode<*>
            ?: return null
        val vf = node.virtualFile ?: return null
        return if (BranchPresentation.isContentRoot(project, vf)) vf else null
    }
}
