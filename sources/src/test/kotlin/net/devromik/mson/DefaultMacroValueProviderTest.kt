package net.devromik.mson

import org.junit.Test
import org.junit.Assert.*

/**
 * @author Shulnyaev Roman
 */
class DefaultMacroValueProviderTest {

    @Test fun inStringValueIsEscapedAsJsonString() {
        val values = mapOf("macroName" to "\"macroValue\"")
        val provider = DefaultMacroValueProvider(values)

        assertEquals("\\\"macroValue\\\"", provider.inString("macroName"))
    }

    @Test fun notInStringValueIsNotEscapedAsJsonString() {
        val values = mapOf("macroName" to "\"macroValue\"")
        val provider = DefaultMacroValueProvider(values)

        assertEquals("\"macroValue\"", provider.notInString("macroName"))
    }
}