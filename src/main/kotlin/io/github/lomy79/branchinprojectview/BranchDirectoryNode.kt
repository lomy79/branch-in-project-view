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
import com.intellij.psi.PsiDirectory

/**
 * A PsiDirectoryNode variant that, for content roots, replaces the path
 * (location string) with the current git branch, colored by branch, plus the
 * status vs origin.
 *
 * We override updateImpl (the same path the node uses to produce the location):
 * unlike a ProjectViewNodeDecorator, these changes are actually rendered by the
 * 2026.x Project view. The Scopes view (and other panes that build their tree from
 * different node types) instead go through [BranchNodeDecorator]; both share the
 * rendering in [BranchPresentation].
 */
class BranchDirectoryNode(project: Project, directory: PsiDirectory, settings: ViewSettings?) :
    PsiDirectoryNode(project, directory, settings) {

    override fun updateImpl(data: PresentationData) {
        super.updateImpl(data)

        val proj = project ?: return
        val vf = virtualFile ?: return
        BranchPresentation.apply(proj, vf, data, name)
    }
}
