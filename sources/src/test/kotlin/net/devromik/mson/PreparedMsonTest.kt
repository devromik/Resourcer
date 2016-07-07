package net.devromik.mson

import org.junit.Assert.*
import org.junit.Test

/**
 * @author Shulnyaev Roman
 */
class PreparedMsonTest {

    @Test fun canExpandMacros() {
        val mson = """
            {
                ${'$'}{asJsonAttrName_asJsonAttrValue}: ${'$'}{asJsonAttrName_asJsonAttrValue},
                "${'$'}{inJsonAttrName_inJsonAttrValue}": "${'$'}{inJsonAttrName_inJsonAttrValue}",

                "${'$'}{notMacro}": "notMacro"
            }
        """

        val expectedJson = """
            {
                " \"asJsonAttrName_asJsonAttrValue\" ": " \"asJsonAttrName_asJsonAttrValue\" ",
                " \"inJsonAttrName_inJsonAttrValue\" ": " \"inJsonAttrName_inJsonAttrValue\" ",

                "${'$'}{notMacro}": "notMacro"
            }
        """

        val macroNames = setOf(
            "asJsonAttrName_asJsonAttrValue",
            "inJsonAttrName_inJsonAttrValue"
        )

        val preparedMson = PreparedMson(mson, macroNames)

        val macroValues = mapOf<String, Any>(
            "asJsonAttrName_asJsonAttrValue" to "\" \\\"asJsonAttrName_asJsonAttrValue\\\" \"",
            "inJsonAttrName_inJsonAttrValue" to " \"inJsonAttrName_inJsonAttrValue\" "
        )

        val macroValueProvider = DefaultMacroValueProvider(macroValues)

        val json = preparedMson.expand(macroValueProvider)
        assertEquals(expectedJson, json)
    }
}