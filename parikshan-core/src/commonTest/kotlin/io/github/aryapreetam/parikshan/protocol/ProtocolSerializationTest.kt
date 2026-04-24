package io.github.aryapreetam.parikshan.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolSerializationTest {

    @Test
    fun testResponseSerialization() {
        val json = """{"type": "error", "id": "123", "message": "test error"}"""
        val resp = ProtocolJson.decodeResponse(json)
        assertTrue(resp is Response.Error)
        assertEquals("123", resp.id)
        assertEquals("test error", (resp as Response.Error).message)

        val okJson = """{"type": "ok", "id": "456"}"""
        val okResp = ProtocolJson.decodeResponse(okJson)
        assertTrue(okResp is Response.Ok)
        assertEquals("456", okResp.id)

        val nodeInfoJson = """{"type": "nodeinfo", "id": "789", "bounds": {"left": 0.0, "top": 0.0, "right": 100.0, "bottom": 100.0}, "visible": true}"""
        val nodeInfoResp = ProtocolJson.decodeResponse(nodeInfoJson)
        assertTrue(nodeInfoResp is Response.NodeInfo)
        assertEquals("789", nodeInfoResp.id)
    }

    @Test
    fun testCommandSerialization() {
        val pingJson = """{"type": "ping", "id": "p1"}"""
        val pingCmd = ProtocolJson.decodeCommand(pingJson)
        assertTrue(pingCmd is Command.Ping)
        assertEquals("p1", pingCmd.id)

        val getTreeJson = """{"type": "gettree", "id": "t1"}"""
        val getTreeCmd = ProtocolJson.decodeCommand(getTreeJson)
        assertTrue(getTreeCmd is Command.GetTree)
        assertEquals("t1", getTreeCmd.id)
    }
}
