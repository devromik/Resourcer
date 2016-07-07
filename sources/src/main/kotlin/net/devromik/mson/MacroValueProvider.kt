package net.devromik.mson

/**
 * @author Shulnyaev Roman
 */
interface MacroValueProvider {
    fun inString(macroName: String): String
    fun notInString(macroName: String): String
}