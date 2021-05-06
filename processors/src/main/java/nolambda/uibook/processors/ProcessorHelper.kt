package nolambda.uibook.processors

import nolambda.uibook.annotations.BookMetaData
import nolambda.uibook.annotations.FunctionParameter
import nolambda.uibook.annotations.UIBook
import nolambda.uibook.annotations.code.CodeSpec
import nolambda.uibook.processors.generator.BookCreatorMetaData
import nolambda.uibook.processors.utils.Logger
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement

class ProcessorHelper(
    private val el: Element,
    private val psiElement: PsiElement,
    private val logger: Logger
) {

    private val psiParameters by lazy {
        (psiElement as? KtFunction)?.valueParameters?.map {
            FunctionParameter(
                name = it.name.orEmpty(),
                type = it.typeReference?.text.orEmpty()
            )
        }.orEmpty()
    }

    private val elParameters by lazy {
        (el as? ExecutableElement)?.parameters?.mapIndexed { index, p ->
            if (index == 0) {
                null
            } else {
                p.asType().toString()
            }
        }?.filterNotNull().orEmpty()
    }

    fun createCreatorMetaData(ktFile: KtFile): BookCreatorMetaData? {
        if (isTheSame().not()) return null

        val codeSpec: CodeSpec? = el.getAnnotation(CodeSpec::class.java)
        val annotation = el.getAnnotation(UIBook::class.java)
        val method = el as ExecutableElement
        val psiMethod = psiElement as KtNamedFunction

        val parameters = elParameters.zip(psiParameters) { elParam, psiParam ->
            FunctionParameter(
                name = psiParam.name,
                type = elParam
            )
        }

        return BookCreatorMetaData(
            annotation = annotation,
            book = BookMetaData(
                name = annotation.name,
                function = getFunctionCode(codeSpec, psiMethod),
                language = codeSpec?.language ?: "kotlin",
                functionName = method.simpleName.toString(),
                packageName = ktFile.packageName,
                parameters = parameters
            )
        )
    }

    private fun getFunctionCode(codeSpec: CodeSpec?, ktFunction: KtNamedFunction): String {
        val function = if (codeSpec?.code?.isNotBlank() == true) {
            codeSpec.code
        } else {
            ktFunction.bodyExpression!!.text
        }
        if (codeSpec != null) {
            if (codeSpec.trimIndent && codeSpec.trimMargin) {
                error("Only one of trimIndent or trimMargin can be true at the same time")
            }
            if (codeSpec.trimIndent) {
                return function.trimIndent()
            }
            if (codeSpec.trimMargin) {
                return function.trimMargin()
            }
        }
        return function
    }

    private fun isTheSame(): Boolean {
        if (psiElement !is KtFunction) return false
        if (el !is ExecutableElement) return false
        if (el.simpleName.toString() != psiElement.name) return false
        if ((el.parameters.size - 1) != psiElement.valueParameters.size) return false

        val isParameterTheSame = psiParameters.mapIndexed { index, t ->
            // TODO: more accurate way to check type
            elParameters[index].contains(t.type, ignoreCase = true)
        }.all { it }

        if (!isParameterTheSame) {
            logger.error("There's a parameter mistmatch that might be false positive: ${el.simpleName}")
        }

        return isParameterTheSame
    }
}
