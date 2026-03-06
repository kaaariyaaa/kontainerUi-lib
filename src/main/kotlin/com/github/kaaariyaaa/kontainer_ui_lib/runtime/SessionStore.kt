package com.github.kaaariyaaa.kontainer_ui_lib.runtime

import java.util.UUID

internal class SessionStore {
    private val bySessionId = mutableMapOf<UUID, WindowSession>()
    private val byPlayerId = mutableMapOf<UUID, UUID>()

    fun put(session: WindowSession) {
        bySessionId[session.id] = session
        byPlayerId[session.playerId] = session.id
    }

    fun bySessionId(id: UUID): WindowSession? = bySessionId[id]

    fun byPlayerId(playerId: UUID): WindowSession? {
        val sessionId = byPlayerId[playerId] ?: return null
        return bySessionId[sessionId]
    }

    fun remove(id: UUID): WindowSession? {
        val removed = bySessionId.remove(id) ?: return null
        byPlayerId.remove(removed.playerId, id)
        return removed
    }

    fun all(): Collection<WindowSession> = bySessionId.values.toList()
}
