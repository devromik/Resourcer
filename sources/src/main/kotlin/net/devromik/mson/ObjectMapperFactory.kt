package net.devromik.mson

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * @author Shulnyaev Roman
 */
interface ObjectMapperFactory {
    fun newObjectMapper(): ObjectMapper
}