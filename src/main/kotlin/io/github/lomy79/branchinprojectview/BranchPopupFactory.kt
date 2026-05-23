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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import git4idea.branch.GitBrancher
import git4idea.repo.GitRepository

/**
 * Builds the branch checkout popup for a specific repository.
 *
 * Prefers the NATIVE Git popup (the same as "Git → Branches": with search,
 * Local/Remote sections, actions), invoked via reflection because its class is
 * `internal` in Kotlin (a compile-time-only restriction; at runtime it is public).
 *
 * If for any reason it is unavailable, it falls back to a simple chooser popup.
 */
object BranchPopupFactory {

    private val LOG = Logger.getInstance(BranchPopupFactory::class.java)

    fun create(project: Project, repository: GitRepository): JBPopup? =
        nativePopup(project, repository) ?: fallbackChooser(project, repository)

    /** Native Git popup for the given repo (git4idea.ui.branch.popup.GitBranchesTreePopupOnBackend). */
    private fun nativePopup(project: Project, repository: GitRepository): JBPopup? = try {
        val clazz = Class.forName("git4idea.ui.branch.popup.GitBranchesTreePopupOnBackend")
        val method = clazz.getMethod("create", Project::class.java, GitRepository::class.java)
        method.invoke(null, project, repository) as? JBPopup
    } catch (t: Throwable) {
        LOG.warn("Native branch popup unavailable, using the fallback", t)
        null
    }

    /** Minimal popup: checkout local branches / create tracking branches from remotes. */
    private fun fallbackChooser(project: Project, repository: GitRepository): JBPopup? {
        val brancher = GitBrancher.getInstance(project)
        val current = repository.currentBranchName
        val actions = LinkedHashMap<String, () -> Unit>()

        repository.branches.localBranches
            .map { it.name }
            .filter { it != current }
            .sorted()
            .forEach { name -> actions[name] = { brancher.checkout(name, false, listOf(repository), null) } }

        val localNames = repository.branches.localBranches.map { it.name }.toSet()
        repository.branches.remoteBranches
            .map { it.name }
            .sorted()
            .forEach { remote ->
                val shortName = remote.substringAfter('/')
                if (shortName !in localNames) {
                    actions["$remote  →  new local branch"] = {
                        brancher.checkoutNewBranchStartingFrom(shortName, remote, listOf(repository), null)
                    }
                }
            }

        if (actions.isEmpty()) return null

        return JBPopupFactory.getInstance()
            .createPopupChooserBuilder(actions.keys.toList())
            .setTitle("Checkout branch — ${repository.root.name}")
            .setItemChosenCallback { chosen -> actions[chosen]?.invoke() }
            .createPopup()
    }
}
