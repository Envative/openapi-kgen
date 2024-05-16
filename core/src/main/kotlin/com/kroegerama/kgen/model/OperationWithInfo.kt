package com.kroegerama.kgen.model

import com.kroegerama.kgen.Constants
import com.kroegerama.kgen.openapi.getRefTypeName
import com.kroegerama.kgen.openapi.getSchemaType
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.ComposedSchema

data class OperationWithInfo(
    val path: String,
    val method: PathItem.HttpMethod,
    val operation: Operation,
    val tags: List<String>,
    val securityNames: List<String>
) {
    fun createOperationName() =
        operation.operationId ?: "${method.name.toLowerCase()}${path.capitalize()}"

    fun getModelNameList(components: Components): Set<String> {
        val set = mutableSetOf<String>()

        val requestContent = operation.requestBody?.content
        if (requestContent != null && requestContent["application/json"] != null) {
            val model = requestContent["application/json"]

            if (!model?.schema?.allOf.isNullOrEmpty()) {
                val refTypeName = model?.schema?.allOf?.firstOrNull()?.getRefTypeName()
                if (refTypeName != null) {
                    set.add(refTypeName)

                    val subModelNames = getSubModelNames(refTypeName, components)
                    if (subModelNames.isNotEmpty()) set.addAll(subModelNames)
                }
            }
        }

        operation.parameters?.forEach {
            if (it.schema.type == "array") {
                it.schema.items.getRefTypeName()?.let { arrayTypeModelName ->
                    set.add(arrayTypeModelName)

                    if (modelHasSubModels(arrayTypeModelName, components))
                        set.addAll(getSubModelNames(arrayTypeModelName, components))
                }
            }
            if (!it.schema?.allOf.isNullOrEmpty()) {
                val refTypeName = it.schema?.allOf?.firstOrNull()?.getRefTypeName()
                if (refTypeName != null) {
                    set.add(refTypeName)

                    val subModelNames = getSubModelNames(refTypeName, components)
                    if (subModelNames.isNotEmpty()) set.addAll(subModelNames)
                }
            }
        }

        operation.responses?.entries?.forEach {
            val resContent = it.value.content
            if (resContent != null && resContent["application/json"] != null) {
                val model = resContent["application/json"]
                val refTypeName = model?.schema?.getRefTypeName()
                if (refTypeName != null) {
                    set.add(refTypeName)

                    val subModelNames = getSubModelNames(refTypeName, components)
                    if (subModelNames.isNotEmpty()) set.addAll(subModelNames)
                }
            }
        }
        return set
    }

    private fun getSubModelNames(refTypeName: String, components: Components): Set<String> {
        val set = mutableSetOf<String>()

        // Navigate recursively through model for submodels
        val modelSchema = components.schemas[refTypeName]
        modelSchema?.properties?.forEach {
            if (it.value.type == "array") {
                it.value.items.getRefTypeName()?.let { arrayTypeModelName ->
                    set.add(arrayTypeModelName)

                    if (modelHasSubModels(arrayTypeModelName, components))
                        set.addAll(getSubModelNames(arrayTypeModelName, components))
                }
            }

            if (it.value is ComposedSchema) {
                it.value.allOf.firstOrNull()?.getRefTypeName()?.let { modelName ->
                    set.add(modelName)

                    if (modelHasSubModels(modelName, components))
                        set.addAll(getSubModelNames(modelName, components))
                }
            }
        }

        return set
    }

    private fun modelHasSubModels(refTypeName: String, components: Components): Boolean {
        val modelSchema = components.schemas[refTypeName]
        modelSchema?.properties?.forEach {
            if (it.value.type == "array") {
                it.value.items.getRefTypeName()?.let {
                    return true
                }
            }

            if (it.value is ComposedSchema) {
                it.value.allOf.firstOrNull()?.getRefTypeName()?.let {
                    return true
                }
            }
        }

        return false
    }

    fun getRequest(): SchemaWithMime? {
        val requestBody = operation.requestBody ?: return null
        val required = requestBody.required ?: false

        val contentTypes = requestBody.content.orEmpty().entries

        //find supported mime, use first one as fallback
        val (mime, mediaType) = contentTypes.firstOrNull { (mime, _) ->
            mime in preferredMimes
        } ?: contentTypes.firstOrNull() ?: return null

        return SchemaWithMime(mime, required, mediaType.schema)
    }

    fun getResponse(): ResponseInfo? {
        val responses = operation.responses ?: return null
        val responseEntries = responses.entries

        //find first success response, use first one as fallback
        val (codeStr, response) = responseEntries.firstOrNull { (code, _) ->
            code.toIntOrNull() in 200..299
        } ?: responseEntries.firstOrNull() ?: return null
        val code = codeStr.toInt()

        val description: String? = response.description

        val contentEntries = response.content.orEmpty().entries
        val (mime, mediaType) = contentEntries.firstOrNull { (mime, _) ->
            mime == Constants.MIME_TYPE_JSON
        } ?: contentEntries.firstOrNull() ?: return ResponseInfo(code, description, null)

        return ResponseInfo(
            code,
            description,
            SchemaWithMime(mime, true, mediaType.schema)
        )
    }

    override fun toString(): String {
        return "OperationWithInfo(path='$path', method=$method, operation=${operation.operationId}, tags=$tags, securityNames=$securityNames)"
    }

    companion object {
        private val preferredMimes = listOf(
            Constants.MIME_TYPE_JSON,
            Constants.MIME_TYPE_MULTIPART_FORM_DATA,
            Constants.MIME_TYPE_URL_ENCODED
        )
    }
}