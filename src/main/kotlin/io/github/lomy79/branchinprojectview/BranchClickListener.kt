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
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.tree.TreeUtil
import git4idea.repo.GitRepositoryManager
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import javax.swing.JTree
import javax.swing.tree.TreePath

/**
 * Attaches [BranchClickListener] to the Project view tree as soon as it is
 * available after the project opens.
 */
class BranchClickActivity : ProjectActivity {
    override suspend fun execute(project: Project) = attachWhenReady(project, attempt = 0)

    private fun attachWhenReady(project: Project, attempt: Int) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val tree = ProjectView.getInstance(project).currentProjectViewPane?.tree
            when {
                tree != null && tree.getClientProperty(ATTACHED) != true -> {
                    tree.putClientProperty(ATTACHED, true)
                    tree.addMouseListener(BranchClickListener(project, tree))
                }
                tree == null && attempt < 40 -> AppExecutorUtil.getAppScheduledExecutorService()
                    .schedule({ attachWhenReady(project, attempt + 1) }, 500, TimeUnit.MILLISECONDS)
            }
        }
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
        val node = branchNodeAt(path) ?: return
        val vf = node.virtualFile ?: return
        val repo = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(vf) ?: return

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

    private fun branchNodeAt(path: TreePath): BranchDirectoryNode? {
        val uo = TreeUtil.getLastUserObject(path) ?: return null
        return uo as? BranchDirectoryNode
            ?: ((uo as? NodeDescriptor<*>)?.element as? BranchDirectoryNode)
    }
}
