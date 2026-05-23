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

import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager

/**
 * Replaces the PsiDirectoryNode nodes that represent a content root (the "modules"
 * shown at the top level of the Project view) with [BranchDirectoryNode], which shows
 * the git branch instead of the path. All other nodes are left unchanged.
 */
class BranchTreeStructureProvider(private val project: Project) : TreeStructureProvider {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: Collection<AbstractTreeNode<*>>,
        settings: ViewSettings,
    ): Collection<AbstractTreeNode<*>> {
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots.toHashSet()
        if (contentRoots.isEmpty()) return children

        return children.map { child ->
            val dirNode = child as? PsiDirectoryNode
            val dir = dirNode?.value
            val vf = dirNode?.virtualFile
            if (dir != null && vf != null && vf in contentRoots) {
                BranchDirectoryNode(project, dir, settings)
            } else {
                child
            }
        }
    }
}
