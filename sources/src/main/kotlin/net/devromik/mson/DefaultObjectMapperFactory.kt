package net.devromik.mson

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * @author Shulnyaev Roman
 */
val DEFAULT_OBJECT_MAPPER_FACTORY = DefaultObjectMapperFactory()

class DefaultObjectMapperFactory : ObjectMapperFactory {
    override fun newObjectMapper() = ObjectMapper()
}