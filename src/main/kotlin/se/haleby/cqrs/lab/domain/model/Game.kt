package se.haleby.cqrs.lab.domain.model

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventhandling.EventHandler
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle.apply
import org.axonframework.modelling.command.AggregateRoot
import se.haleby.cqrs.lab.domain.command.CreateGame
import se.haleby.cqrs.lab.domain.command.MakeMove
import se.haleby.cqrs.lab.domain.event.*
import java.util.*

typealias GameId = String
typealias PlayerId = String

data class PlayerMove(val playerId: PlayerId, val move: Move) {
    fun isMadeBy(playerId: PlayerId) = this.playerId == playerId
    fun madeSameMoveAs(otherPlayerMove: PlayerMove): Boolean = move == otherPlayerMove.move
    fun beats(otherPlayerMove: PlayerMove): Boolean = move.beats(otherPlayerMove.move)
}

sealed class GameState
object Created : GameState()
object Ongoing : GameState()
object Ended : GameState()

class GameAlreadyStartedException : RuntimeException("Game already started")
class GameAlreadyEndedException : RuntimeException("Game already ended")
class PlayerAlreadyPlayedException : RuntimeException("Player already ended")

@AggregateRoot
class Game internal constructor() {

    @AggregateIdentifier
    private lateinit var gameId: GameId
    private lateinit var createdAt: Date
    private var gameState: GameState? = null
    private var playerMove1: PlayerMove? = null
    private var playerMove2: PlayerMove? = null

    @CommandHandler
    constructor(cmd: CreateGame) : this() {
        if (gameState != null) {
            throw GameAlreadyStartedException()
        }
        apply(GameCreated(cmd.gameId, cmd.timestamp))
    }

    @CommandHandler
    fun handle(cmd: MakeMove) {
        when (gameState!!) {
            Created -> apply(listOf(GameStarted(gameId, cmd.playerId), MoveMade(gameId, cmd.timestamp, cmd.playerId, cmd.move)))
            Ongoing -> if (hasAlreadyPlayed(cmd.playerId)) {
                throw PlayerAlreadyPlayedException()
            } else {
                apply(MoveMade(gameId, cmd.timestamp, cmd.playerId, cmd.move))

                val pm1 = playerMove1!!
                val pm2 = playerMove2!!

                val result = if (pm1.madeSameMoveAs(pm2)) {
                    GameTied(gameId)
                } else {
                    val player1BeatPlayer2 = pm1.beats(pm2)
                    GameWon(gameId, if (player1BeatPlayer2) pm1.playerId else pm2.playerId, if (player1BeatPlayer2) pm2.playerId else pm1.playerId)
                }
                apply(listOf(result, GameEnded(gameId)))
            }
            Ended -> throw GameAlreadyEndedException()
        }
    }

    @EventHandler
    fun on(evt: GameCreated) {
        gameId = evt.gameId
        createdAt = evt.timestamp
        gameState = Created
    }

    @EventHandler
    fun on(evt: GameStarted) {
        gameState = Ongoing
    }

    @EventHandler
    fun on(evt: GameEnded) {
        gameState = Ended
    }

    @EventHandler
    fun on(evt: MoveMade) {
        val playerMove = PlayerMove(evt.playerId, evt.move)
        if (playerMove1 == null) {
            playerMove1 = playerMove
        } else {
            playerMove2 = playerMove
        }
    }

    private fun hasAlreadyPlayed(playerId: PlayerId) = sequenceOf(playerMove1, playerMove2).filterNotNull().any { playerMove -> playerMove.isMadeBy(playerId) }
}