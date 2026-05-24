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
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator

/**
 * Shows the git branch next to content roots in the panes that do not build their tree
 * from [com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode] — most notably the
 * **Scopes** view, whose directories are private node types that we cannot replace via a
 * TreeStructureProvider. Those nodes call [ProjectViewNodeDecorator.decorate] (and render
 * the resulting colored fragments), so we hook in here instead.
 *
 * The standard Project view is handled by [BranchDirectoryNode]; we skip it here to avoid
 * decorating the same content root twice.
 */
class BranchNodeDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        // Already handled by the replaced node in the Project view.
        if (node is BranchDirectoryNode) return

        val project = node.project ?: return
        val vf = node.virtualFile ?: return
        if (!BranchPresentation.isContentRoot(project, vf)) return

        BranchPresentation.apply(project, vf, data, vf.name)
    }
}
