package io.github.saadahmed1122.bibi.ai.inference.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InferenceProtocolTest {

    private fun roundTrip(message: ProtocolMessage) =
        assertEquals(message, InferenceProtocol.decode(InferenceProtocol.encode(message)))

    @Test
    fun clientToServiceMessagesRoundTrip() {
        roundTrip(ProtocolMessage.Register)
        roundTrip(ProtocolMessage.Load("/data/local/tmp/model.litertlm"))
        roundTrip(ProtocolMessage.Send(requestId = 7, text = "Hi Bibi\nhow are you?"))
        roundTrip(ProtocolMessage.Stop(requestId = 7))
        roundTrip(ProtocolMessage.Reset)
        roundTrip(ProtocolMessage.LoadRouter("/data/user/0/app/files/models/router.litertlm"))
        roundTrip(ProtocolMessage.Route(requestId = 9, text = "set a timer for 5 minutes"))
        roundTrip(ProtocolMessage.SetPersona(Persona.CONCISE))
        roundTrip(ProtocolMessage.SetMemory("What you know about the user: My name is Saad."))
        roundTrip(ProtocolMessage.SetMemory(""))
    }

    @Test
    fun routerAnswersRoundTrip() {
        roundTrip(ProtocolMessage.Routed(9, RouteResult.Label("start_timer", 0.4123f)))
        roundTrip(ProtocolMessage.Routed(9, RouteResult.Label("none", 0f)))
        roundTrip(ProtocolMessage.Routed(9, RouteResult.Unavailable("router not loaded")))
    }

    @Test
    fun aRoutedAnswerWithoutAConfidenceOrReasonDecodesToNull() {
        assertNull(
            InferenceProtocol.decode(
                Envelope(
                    InferenceProtocol.MSG_ROUTED,
                    mapOf(InferenceProtocol.KEY_REQUEST_ID to "9", InferenceProtocol.KEY_LABEL to "start_timer"),
                ),
            ),
        )
        assertNull(InferenceProtocol.decode(Envelope(InferenceProtocol.MSG_ROUTED, mapOf(InferenceProtocol.KEY_REQUEST_ID to "9"))))
    }

    @Test
    fun everyEngineStateRoundTrips() {
        listOf(
            EngineState.Unbound,
            EngineState.Loading,
            EngineState.Ready,
            EngineState.Generating,
            EngineState.Failed("out of memory"),
        ).forEach { roundTrip(ProtocolMessage.State(it)) }
    }

    @Test
    fun everyReplyEventRoundTrips() {
        listOf(
            ReplyEvent.Token(" hel"),
            ReplyEvent.Token(""),
            ReplyEvent.Done("hello"),
            ReplyEvent.Stopped("hel"),
            ReplyEvent.Error("engine crashed", "he"),
        ).forEach { roundTrip(ProtocolMessage.Reply(requestId = 42, event = it)) }
    }

    @Test
    fun eachMessageTypeHasItsOwnCode() {
        val codes = listOf(
            ProtocolMessage.Register,
            ProtocolMessage.Load("p"),
            ProtocolMessage.Send(1, "t"),
            ProtocolMessage.Stop(1),
            ProtocolMessage.Reset,
            ProtocolMessage.LoadRouter("p"),
            ProtocolMessage.Route(1, "t"),
            ProtocolMessage.SetPersona(Persona.FRIENDLY),
            ProtocolMessage.SetMemory(""),
            ProtocolMessage.State(EngineState.Ready),
            ProtocolMessage.Reply(1, ReplyEvent.Token("a")),
            ProtocolMessage.Reply(1, ReplyEvent.Done("a")),
            ProtocolMessage.Reply(1, ReplyEvent.Stopped("a")),
            ProtocolMessage.Reply(1, ReplyEvent.Error("a", "a")),
            ProtocolMessage.Routed(1, RouteResult.Label("none", 0f)),
        ).map { InferenceProtocol.encode(it).what }
        assertEquals(
            listOf(
                InferenceProtocol.MSG_REGISTER,
                InferenceProtocol.MSG_LOAD,
                InferenceProtocol.MSG_SEND,
                InferenceProtocol.MSG_STOP,
                InferenceProtocol.MSG_RESET,
                InferenceProtocol.MSG_LOAD_ROUTER,
                InferenceProtocol.MSG_ROUTE,
                InferenceProtocol.MSG_SET_PERSONA,
                InferenceProtocol.MSG_SET_MEMORY,
                InferenceProtocol.MSG_STATE,
                InferenceProtocol.MSG_TOKEN,
                InferenceProtocol.MSG_DONE,
                InferenceProtocol.MSG_STOPPED,
                InferenceProtocol.MSG_ERROR,
                InferenceProtocol.MSG_ROUTED,
            ),
            codes,
        )
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun unknownOrMalformedEnvelopesDecodeToNull() {
        assertNull(InferenceProtocol.decode(Envelope(what = 9999, fields = emptyMap())))
        assertNull(
            InferenceProtocol.decode(
                Envelope(InferenceProtocol.MSG_SEND, mapOf(InferenceProtocol.KEY_TEXT to "no request id")),
            ),
        )
        assertNull(
            InferenceProtocol.decode(
                Envelope(
                    InferenceProtocol.MSG_TOKEN,
                    mapOf(InferenceProtocol.KEY_REQUEST_ID to "not-a-number", InferenceProtocol.KEY_TEXT to "x"),
                ),
            ),
        )
        assertNull(
            InferenceProtocol.decode(
                Envelope(InferenceProtocol.MSG_STATE, mapOf(InferenceProtocol.KEY_STATE to "Bogus")),
            ),
        )
    }
}
