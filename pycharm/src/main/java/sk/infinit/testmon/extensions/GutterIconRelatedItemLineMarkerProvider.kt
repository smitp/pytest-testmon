package sk.infinit.testmon.extensions

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.psi.PsiElement
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.extensions.python.toPsi
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStatement
import sk.infinit.testmon.*
import sk.infinit.testmon.database.FileMarkType
import sk.infinit.testmon.database.PyFileMark
import sk.infinit.testmon.services.cache.Cache

/**
 * Testmon RelatedItemLineMarkerProvider fod display gutter icons.
 */
class GutterIconRelatedItemLineMarkerProvider : RelatedItemLineMarkerProvider() {

    /**
     * Add Line Marker Information to Gutter area.
     */
    override fun collectNavigationMarkers(psiElement: PsiElement, resultCollection: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
        if (psiElement is PyStatement) {
            val project = psiElement.project


            val fileFullPath = getFileFullPath(project, psiElement.containingFile.virtualFile)
                    ?: return

            val cacheService = ServiceManager.getService(project, Cache::class.java)
                    ?: return

            val pyFileMarks = cacheService.getPyFileMarks(fileFullPath, FileMarkType.GUTTER_LINK) ?: return

            for (fileMark in pyFileMarks) {
                val targetVirtualFile = findVirtualFile(fileMark.targetPath)

                val fileMarkContent = fileMark.checkContent.trim()

                if (targetVirtualFile != null && fileMarkContent == psiElement.text) {
                    val targetPsiElement = findTargetPsiElement(fileMark, project, targetVirtualFile) ?: continue

                    val navigationGutterIconBuilder = NavigationGutterIconBuilder
                            .create(AllIcons.General.Error)
                            .setTarget(targetPsiElement)
                            .setTooltipText("File ${targetVirtualFile.name}, Line ${fileMark.targetLine}")

                    val leafElement =  getFirstLeafElement(psiElement)
                    resultCollection.add(navigationGutterIconBuilder.createLineMarkerInfo(leafElement))
                }
            }
        }
    }

    private fun getFirstLeafElement(psiElement: PsiElement): PsiElement {
        val firstChild = psiElement.firstChild
        return if (firstChild  == null){
            psiElement
        } else {
            getFirstLeafElement(firstChild)
        }
    }

    /**
     * Get target PsiElement to navigate.
     */
    private fun findTargetPsiElement(fileMark: PyFileMark, project: Project, targetVirtualFile: VirtualFile): PsiElement? {
        val targetPsiFile = targetVirtualFile.toPsi(project) as PyFile

        val document = targetPsiFile.viewProvider.document ?: return null

        val targetLine = fileMark.targetLine

        val lineNumber = if (targetLine == document.lineCount) {
            targetLine - 1
        } else {
            targetLine
        }

        if (targetLine >= document.lineCount) {
            return null
        }

        return targetPsiFile.findElementAt(document.getLineStartOffset(lineNumber))
    }
}