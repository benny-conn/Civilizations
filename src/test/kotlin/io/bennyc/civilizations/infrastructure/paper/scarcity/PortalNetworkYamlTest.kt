package io.bennyc.civilizations.infrastructure.paper.scarcity

import kotlin.test.*

class PortalNetworkYamlTest {
    private val source="""
        format: 1
        season: 00000000-0000-0000-0000-000000000010
        pairs:
          - id: crossing
            first: {world: 'minecraft:overworld', uuid: '00000000-0000-0000-0000-000000000001', min-x: 0, min-y: 64, min-z: 0, max-x: 1, max-y: 66, max-z: 0}
            second: {world: 'minecraft:the_nether', uuid: '00000000-0000-0000-0000-000000000002', min-x: 0, min-y: 64, min-z: 0, max-x: 1, max-y: 66, max-z: 0}
    """.trimIndent()
    @Test fun `strict format accepts pairs and rejects malformed geometry identity and keys`() {
        assertEquals(1,PortalNetworkYaml.parse(source.toByteArray()).pairs.size)
        listOf(source+"\nformat: 1",source+"\nextra: 1",source.replace("max-y: 66","max-y: 65"),
            source.replace("max-x: 1","max-x: 1.5"),source.replace("format: 1","format: 2"),
            source.replace("00000000-0000-0000-0000-000000000010","1-1-1-1-1")).forEach {
            assertFailsWith<IllegalArgumentException> { PortalNetworkYaml.parse(it.toByteArray()) }
        }
        assertFailsWith<IllegalArgumentException> { PortalNetworkYaml.parse(ByteArray(1_048_577)) }
    }
}
