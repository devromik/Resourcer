package net.devromik.mson

import org.apache.commons.lang3.StringEscapeUtils.escapeJson

/**
 * @author Shulnyaev Roman
 */
class DefaultMacroValueProvider(val values: Map<String, Any>) : MacroValueProvider {
    override fun inString(macroName: String) = escapeJson(values[macroName].toString())
    override fun notInString(macroName: String) = values[macroName].toString()
}