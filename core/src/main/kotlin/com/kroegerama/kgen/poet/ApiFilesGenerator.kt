package com.kroegerama.kgen.poet

import com.kroegerama.kgen.Constants
import com.kroegerama.kgen.OptionSet
import com.kroegerama.kgen.language.asClassFileName
import com.kroegerama.kgen.language.asFieldName
import com.kroegerama.kgen.language.asFunctionName
import com.kroegerama.kgen.language.asTypeName
import com.kroegerama.kgen.model.OperationWithInfo
import com.kroegerama.kgen.model.ResponseInfo
import com.kroegerama.kgen.model.SchemaWithMime
import com.kroegerama.kgen.openapi.OpenAPIAnalyzer
import com.kroegerama.kgen.openapi.OperationRequestType
import com.kroegerama.kgen.openapi.mapMimeToRequestType
import com.kroegerama.kgen.openapi.mapToParameterType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.parameters.Parameter
import io.reactivex.Observable

interface IApiFilesGenerator {
    fun getApiFiles(): List<FileSpec>
}

class ApiFilesGenerator(
    openAPI: OpenAPI,
    options: OptionSet,
    analyzer: OpenAPIAnalyzer,
    private val isObservableApiSyntaxType: Boolean = false,
    private val useCompanionObjects: Boolean = false,
    private val useApiSpecEnumNames: Boolean = false
) : IApiFilesGenerator,
    IPoetGeneratorBase by PoetGeneratorBase(openAPI, options, analyzer),
    IPoetGeneratorSchemaHandler by PoetGeneratorSchemaHandler(openAPI, options, analyzer, useCompanionObjects, useApiSpecEnumNames) {

    override fun getApiFiles(): List<FileSpec> = analyzer.apis.map { (name, operations) ->
        val apiName = "$name api"
        val cnHolder = ClassName(options.packageName, "ApiHolder")
        prepareFileSpec(options.apiPackage, apiName.asClassFileName()) {
            val className = ClassName(options.apiPackage, apiName.asTypeName())
            val apiInterface = poetInterface(className) {
                addAnnotation(JvmSuppressWildcards::class)

                val companion = TypeSpec.companionObjectBuilder()

                if (isObservableApiSyntaxType) {
                    operations.forEach { opInfo ->
                        handleOperationInfoForObservables(
                            cnHolder,
                            className,
                            this@poetInterface,
                            companion,
                            opInfo
                        )
                    }
                } else {
                    operations.forEach { opInfo ->
                        handleOperationInfo(
                            cnHolder,
                            className,
                            this@poetInterface,
                            companion,
                            opInfo
                        )
                    }
                }

                addType(companion.build())
            }
            addType(apiInterface)
        }
    }

    private fun handleOperationInfo(
        cnHolder: ClassName,
        apiClassName: ClassName,
        apiInterface: TypeSpec.Builder,
        companion: TypeSpec.Builder,
        operationInfo: OperationWithInfo
    ) {
        val funName = operationInfo.createOperationName().asFunctionName()
        val request = operationInfo.getRequest()
        val response = operationInfo.getResponse()

        val baseParams = collectParameters(operationInfo)
        val methodParams = request?.let { getAdditionalParameters(it) }.orEmpty()
        val allParameters = baseParams + methodParams

        val ifaceFun = poetFunSpec("__$funName") {
            val methodAnnotation =
                createHttpMethodAnnotation(operationInfo.method, operationInfo.path)
            addAnnotation(methodAnnotation)

            if (operationInfo.securityNames.isNotEmpty()) {
                val cnInterceptor = ClassName(options.packageName, "ApiAuthInterceptor")
                val mnAuthHeader = MemberName(cnInterceptor, Constants.AUTH_HEADER_NAME)

                val secHeader = poetAnnotation(PoetConstants.RETROFIT_HEADERS) {
                    operationInfo.securityNames.forEach { name ->
                        //val secStr = "${Constants.AUTH_HEADER_VALUE}: ${scheme.name}"
                        val block = buildCodeBlock {
                            add("\${%M}: %L", mnAuthHeader, name)
                        }

                        addMember("\"%L\"", block)
                    }
                }
                addAnnotation(secHeader)
            }

            when (request?.mime) {
                Constants.MIME_TYPE_MULTIPART_FORM_DATA -> addAnnotation(PoetConstants.RETROFIT_MULTIPART)
                Constants.MIME_TYPE_URL_ENCODED -> addAnnotation(PoetConstants.RETROFIT_FORM_ENCODED)
            }

            addModifiers(KModifier.SUSPEND, KModifier.ABSTRACT)
            addParameters(allParameters.map { it.ifaceParam }.sortedBy { ifaceParam ->
                //@Path must be defined before all other params
                ifaceParam.annotations.any { it.typeName != PoetConstants.RETROFIT_PARAM_PATH }
            }.sortedBy { ifaceParam ->
                //@Body must be the last param
                ifaceParam.annotations.any { it.typeName == PoetConstants.RETROFIT_BODY }
            })
            addReturns(response, false)
        }

        val delegateFun = poetFunSpec(funName) {
            operationInfo.operation.description?.let {
                addKdoc("%L", it)
            }
            addModifiers(KModifier.SUSPEND)
            addParameters(allParameters.map { it.delegateParam })

            val paramList = parameters.joinToString(
                ",\n    ",
                prefix = "\n",
                postfix = "\n"
            ) { it.name + " = " + it.name }
            addStatement(
                "return %T.getApi<%T>().%L(%L)",
                cnHolder,
                apiClassName,
                "__$funName",
                paramList
            )

            addReturns(response, true)
        }

        apiInterface.addFunction(ifaceFun)
        companion.addFunction(delegateFun)
    }

    private fun handleOperationInfoForObservables(
        cnHolder: ClassName,
        apiClassName: ClassName,
        apiInterface: TypeSpec.Builder,
        companion: TypeSpec.Builder,
        operationInfo: OperationWithInfo
    ) {
        val funName = operationInfo.createOperationName().asFunctionName()
        val request = operationInfo.getRequest()
        val response = operationInfo.getResponse()

        val baseParams = collectParameters(operationInfo)
        val methodParams = request?.let { getAdditionalParameters(it) }.orEmpty()
        val allParameters = baseParams + methodParams

        val ifaceFun = poetFunSpec("__$funName") {
            val methodAnnotation =
                createHttpMethodAnnotation(operationInfo.method, operationInfo.path)
            addAnnotation(methodAnnotation)

            if (operationInfo.securityNames.isNotEmpty()) {
                val cnInterceptor = ClassName(options.packageName, "ApiAuthInterceptor")
                val mnAuthHeader = MemberName(cnInterceptor, Constants.AUTH_HEADER_NAME)

                val secHeader = poetAnnotation(PoetConstants.RETROFIT_HEADERS) {
                    operationInfo.securityNames.forEach { name ->
                        //val secStr = "${Constants.AUTH_HEADER_VALUE}: ${scheme.name}"
                        val block = buildCodeBlock {
                            add("\${%M}: %L", mnAuthHeader, name)
                        }

                        addMember("\"%L\"", block)
                    }
                }
                addAnnotation(secHeader)
            }

            when (request?.mime) {
                Constants.MIME_TYPE_MULTIPART_FORM_DATA -> addAnnotation(PoetConstants.RETROFIT_MULTIPART)
                Constants.MIME_TYPE_URL_ENCODED -> addAnnotation(PoetConstants.RETROFIT_FORM_ENCODED)
            }

            addModifiers(KModifier.ABSTRACT)
            addParameters(allParameters.map { it.ifaceParam }.sortedBy { ifaceParam ->
                //@Path must be defined before all other params
                ifaceParam.annotations.any { it.typeName != PoetConstants.RETROFIT_PARAM_PATH }
            }.sortedBy { ifaceParam ->
                //@Body must be the last param
                ifaceParam.annotations.any { it.typeName == PoetConstants.RETROFIT_BODY }
            })
            addObservableReturns(response, false)
        }

        val delegateFun = poetFunSpec(funName) {
            operationInfo.operation.description?.let {
                addKdoc("%L", it)
            }
            addParameters(allParameters.map { it.delegateParam })

            val paramList = parameters.joinToString(
                ",\n    ",
                prefix = "\n",
                postfix = "\n"
            ) { it.name + " = " + it.name }
            addStatement(
                "return %T.getApi<%T>().%L(%L)",
                cnHolder,
                apiClassName,
                "__$funName",
                paramList
            )

            addObservableReturns(response, true)
        }

        apiInterface.addFunction(ifaceFun)
        companion.addFunction(delegateFun)
    }

    private fun collectParameters(operationInfo: OperationWithInfo) =
        operationInfo.operation.parameters.orEmpty().map { parameter ->
            createParameterSpecPair(parameter)
        }

    private fun createParameterSpecPair(parameter: Parameter): ParameterSpecPairInfo {
        val rawName = parameter.name
        val name = rawName.asFieldName()
        val schema = parameter.schema
        val paramType = parameter.mapToParameterType()

        val type = analyzer.findTypeNameFor(schema).let { typeName ->
            if (parameter.required) {
                typeName
            } else {
                typeName.copy(nullable = true)
            }
        }

        val ifaceParam = poetParameter(name, type) {
            addAnnotation(createParameterAnnotation(paramType, rawName))
        }
        val delegateParam = poetParameter(name, type) {
            //TODO add schema.default as defaultValue
            parameter.description?.let {
                addKdoc("%L", it)
            }
            if (!parameter.required) {
                defaultValue("null")
            }
        }
        return ParameterSpecPairInfo(ifaceParam, delegateParam)
    }

    private fun getAdditionalParameters(request: SchemaWithMime): List<ParameterSpecPairInfo> {
        val (mime, required, schema) = request
        return when (mime.mapMimeToRequestType()) {
            OperationRequestType.Default -> {
                val typeName = analyzer.findTypeNameFor(schema)
                val ifaceBodyParam = poetParameter("body", typeName.nullable(!required)) {
                    addAnnotation(PoetConstants.RETROFIT_BODY)
                }
                val delegateBodyParam = poetParameter("body", typeName.nullable(!required)) {
                    if (!required) defaultValue("null")
                    addAnnotation(PoetConstants.RETROFIT_BODY)
                }
                listOf(ParameterSpecPairInfo(ifaceBodyParam, delegateBodyParam))
            }

            OperationRequestType.Multipart -> {
                schema.convertToParameters(required, true)
            }

            OperationRequestType.UrlEncoded -> {
                schema.convertToParameters(required, false)
            }

            OperationRequestType.Unknown -> {
                //TODO!!
                emptyList()
            }
        }
    }

    private fun FunSpec.Builder.addReturns(responseInfo: ResponseInfo?, withDescription: Boolean) {
        responseInfo?.let { (_, description, schemaWithMime) ->
            val descriptionBlock = if (withDescription)
                CodeBlock.Builder().apply {
                    description?.let { add("%L", it) }
                }.build() else CodeBlock.builder().build()

            schemaWithMime?.let { (mime, _, schema) ->
                val typeName = analyzer.findTypeNameFor(schema)
                val responseType = PoetConstants.RETROFIT_RESPONSE.parameterizedBy(typeName)

                if (mime == Constants.MIME_TYPE_JSON) {
                    returns(responseType, descriptionBlock)
                } else {
                    returns(PoetConstants.OK_RESPONSE_BODY, descriptionBlock)
                }
            } ?: returns(PoetConstants.RETROFIT_RESPONSE.parameterizedBy(UNIT), descriptionBlock)
        }
    }

    private fun FunSpec.Builder.addObservableReturns(
        responseInfo: ResponseInfo?,
        withDescription: Boolean
    ) {
        responseInfo?.let { (_, description, schemaWithMime) ->
            val descriptionBlock = if (withDescription)
                CodeBlock.Builder().apply {
                    description?.let { add("%L", it) }
                }.build() else CodeBlock.builder().build()

            schemaWithMime?.let { (mime, _, schema) ->
                val typeName = analyzer.findTypeNameFor(schema)
                val observableType = Observable::class.java.asClassName().parameterizedBy(typeName)

                if (mime == Constants.MIME_TYPE_JSON) {
                    returns(observableType, descriptionBlock)
                } else {
                    returns(PoetConstants.OK_RESPONSE_BODY, descriptionBlock)
                }
            } ?: returns(
                PoetConstants.RETROFIT_CALL.parameterizedBy(PoetConstants.OK_RESPONSE_BODY),
                descriptionBlock
            )
        }
    }
}