package net.devromik.mson

import com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BigIntegerNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.lang.Character.isDigit
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*
import java.util.UUID.randomUUID

/**
 * @author Shulnyaev Roman
 */
val MAX_MSON_MACRO_COUNT = 1000000000

class PreparedMson(
    val mson: String,
    val macroNames: Set<String>,
    val macroStartTag: String = "\${",
    val macroEndTag: String = "}",
    val objectMapperFactory: ObjectMapperFactory = DEFAULT_OBJECT_MAPPER_FACTORY) {

    private val macroStartTagLength = macroStartTag.length
    private val macroStartTagFirstChar: Char
    private val macroEndTagLength = macroEndTag.length

    private val elements = ArrayList<Element>()
    private val macroElements = HashMap<BigInteger, MacroElement>()

    private var encodedJsonNode: JsonNode = NullNode.instance

    // ****************************** //

    init {
        require(macroStartTagLength > 0)
        macroStartTagFirstChar = macroStartTag.first()

        require(macroEndTagLength > 0)

        parse()
    }

    // ****************************** //

    fun expand(macroValueProvider: MacroValueProvider): String {
        if (elements.size == 1 && elements.first() is StringElement) {
            return (elements.first() as StringElement).string
        }

        val builder = StringBuilder()
        elements.forEach { builder.append(it.expand(macroValueProvider)) }

        return builder.toString()
    }

    // ****************************** //

    private interface Element {
        fun expand(macroValueProvider: MacroValueProvider): String
    }

    private class StringElement(val string: String) : Element {
        override fun expand(macroValueProvider: MacroValueProvider) = string
    }

    private class MacroElement(
        val macroName: String,
        val pos: Int,
        val code: BigInteger,
        var inString: Boolean) : Element {

        override fun expand(macroValueProvider: MacroValueProvider) =
            if (inString) macroValueProvider.inString(macroName) else macroValueProvider.notInString(macroName)
    }

    // ****************************** //

    private fun parse() {
        var stringElementBuilder = StringBuilder()
        var macroCode = firstMacroCodeFor(mson)
        var pos = 0

        while (pos < mson.length) {
            val currChar = mson.elementAt(pos)
            val macroElement = if (currChar == macroStartTagFirstChar) tryParseMacroElement(pos, macroCode) else null

            if (macroElement != null) {
                if (stringElementBuilder.length > 0) {
                    val stringElement = StringElement(stringElementBuilder.toString())
                    elements.add(stringElement)
                    stringElementBuilder = StringBuilder()
                }

                elements.add(macroElement)
                pos += macroStartTagLength + macroElement.macroName.length + macroEndTagLength
                macroCode = nextMacroCodeFor(macroCode)
            }
            else {
                stringElementBuilder.append(currChar)
                ++pos
            }
        }

        if (stringElementBuilder.length > 0) {
            val stringElement = StringElement(stringElementBuilder.toString())
            elements.add(stringElement)
        }

        markNotInStringMacros()
    }

    private fun tryParseMacroElement(startPos: Int, macroCode: BigInteger): MacroElement? {
        if (mson.length - startPos < macroStartTagLength) {
            return null
        }

        var pos = startPos

        for (i in 0..macroStartTagLength - 1) {
            if (mson.elementAt(pos + i) != macroStartTag.elementAt(i)) {
                return null
            }
        }

        pos = startPos + macroStartTagLength
        var foundMacroName: String? = null

        tryEveryMacro@
        for (macroName in macroNames) {
            val macroNameLength = macroName.length

            if (mson.length - pos < macroNameLength) {
                continue
            }

            for (i in 0..macroNameLength - 1) {
                if (mson.elementAt(pos + i) != macroName.elementAt(i)) {
                    continue@tryEveryMacro
                }
            }

            pos += macroNameLength

            for (i in 0..macroEndTagLength - 1) {
                if (mson.elementAt(pos + i) != macroEndTag.elementAt(i)) {
                    continue@tryEveryMacro
                }
            }

            foundMacroName = macroName
            break
        }

        if (foundMacroName == null) {
            return null
        }

        val macroElement = MacroElement(foundMacroName, startPos, macroCode, inString = true)
        macroElements.put(macroCode, macroElement)

        return macroElement
    }

    private fun markNotInStringMacros() {
        buildEncodedJsonNode()
        markNotInStringMacrosIn(encodedJsonNode)
    }

    private fun buildEncodedJsonNode() {
        if (encodedJsonNode != NullNode.instance) {
            return
        }

        val encodedJsonBuilder = StringBuilder()

        for (element in elements) {
            when (element) {
                is StringElement -> encodedJsonBuilder.append(element.string)
                is MacroElement -> encodedJsonBuilder.append(element.code)
            }
        }

        val encodedJson = encodedJsonBuilder.toString()

        val objectMapper = objectMapperFactory.newObjectMapper()
        objectMapper.configure(ALLOW_UNQUOTED_FIELD_NAMES, true)

        encodedJsonNode = objectMapper.readTree(encodedJson)
    }

    private fun markNotInStringMacrosIn(encodedJsonNode: JsonNode) {
        when (encodedJsonNode) {
            is ObjectNode -> markNotInStringMacrosIn(encodedObjectJsonNode = encodedJsonNode)
            is ArrayNode -> markNotInStringMacrosIn(encodedArrayJsonNode = encodedJsonNode)
            is BigIntegerNode -> markNotInStringMacrosIn(encodedBigIntegerJsonNode = encodedJsonNode)
        }
    }

    private fun markNotInStringMacrosIn(encodedObjectJsonNode: ObjectNode) {
        val attrNamesIter = encodedObjectJsonNode.fieldNames()

        while (attrNamesIter.hasNext()) {
            val attrName = attrNamesIter.next()

            if (!attrName.isEmpty() && isDigit(attrName.first())) {
                try {
                    val attrNameNumber = BigInteger(attrName)

                    if (macroElements.containsKey(attrNameNumber)) {
                        val macroElement = macroElements[attrNameNumber]!!
                        val macroPos = macroElement.pos

                        if (macroPos > 0 && mson.elementAt(macroPos - 1) != '"') {
                            macroElement.inString = false
                        }
                    }
                }
                catch (ignored: Exception) {}
            }

            val attr = encodedObjectJsonNode.get(attrName)
            markNotInStringMacrosIn(attr)
        }
    }

    private fun markNotInStringMacrosIn(encodedArrayJsonNode: ArrayNode) {
        encodedArrayJsonNode.forEach { it -> markNotInStringMacrosIn(it) }
    }

    private fun markNotInStringMacrosIn(encodedBigIntegerJsonNode: BigIntegerNode) {
        val number = encodedBigIntegerJsonNode.bigIntegerValue()

        if (macroElements.containsKey(number)) {
            val macroElement = macroElements[number]!!
            macroElement.inString = false
        }
    }
}

// ****************************** //

private val DEFAULT_NOT_SCALED_FIRST_MACRO_CODE = bigIntegerOf(randomUUID())
private val MACRO_CODE_SCALE_FACTOR = BigInteger(MAX_MSON_MACRO_COUNT.toString())

// ****************************** //

private fun firstMacroCodeFor(mson: String): BigInteger {
    var notScaledFirstMacroCode = DEFAULT_NOT_SCALED_FIRST_MACRO_CODE

    while (mson.contains(notScaledFirstMacroCode.toString())) {
        notScaledFirstMacroCode = bigIntegerOf(randomUUID())
    }

    return notScaledFirstMacroCode.multiply(MACRO_CODE_SCALE_FACTOR)
}

private fun nextMacroCodeFor(currentMacroCode: BigInteger) = currentMacroCode.add(BigInteger.ONE)

private fun bigIntegerOf(uuid: UUID): BigInteger {
    val buffer = ByteBuffer.wrap(ByteArray(16))
    buffer.putLong(uuid.mostSignificantBits)
    buffer.putLong(uuid.leastSignificantBits)

    return BigInteger(buffer.array()).abs()
}