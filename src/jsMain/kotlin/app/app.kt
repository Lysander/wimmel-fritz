package app

import dev.fritz2.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import model.*
import kotlin.random.Random

class GameStore(initial: GameState) : RootStore<GameState>(initial) {
    val next = handle { old ->
        old.entities.fold(GameState(old.world, emptyList())) { (world, entities), entity ->
            var newEntity = entity.copy(state = entity.state.tick())
            if (newEntity.state.canAct) {
                val newMove = entity.movement.next(world, entity.coordinate, entity.last)
                newEntity = newEntity.moved(newMove)
            }
            GameState(world.update(entity, newEntity), entities + newEntity)
        }
    }

    val spawnOrc = handle<Int> { state, ticks ->
        state.copy(
            entities = state.entities + Entity(
                "O${state.entities.size}",
                Tile.Orc,
                state.world.getStartCoordinate(),
                ActingState(ticks),
                Move.Stay,
                Boucing()
            )
        )
    }

    val spawnTroll = handle<Int> { state, ticks ->
        state.copy(
            entities = state.entities + Entity(
                "T${state.entities.size}",
                Tile.Troll,
                state.world.getStartCoordinate(),
                ActingState(ticks),
                Move.Stay,
                KeepOn()
            )
        )
    }

    val cycleTerrain = handle<Int> { state, index ->
        //state.copy(world = state.world.fields.mapIndexed { i, field -> if(index) })
        state
    }

    val killAll = handle {
        GameState(it.world.clearEntities(), emptyList())
    }

    val reset = handle {
        GameState(generateWorld(), emptyList())
    }

}

fun main() {
    val game = GameStore(
        GameState(
            generateWorld(),
            emptyList()
        ))

    val storedFields = game.sub(GameState.world() + World.fields())

    val storedTickspeed = storeOf(100L)
    val tick = generateSequence(1) { it + 1 }.asFlow()

    render {
        div("w-full h-screen flex items-center justify-center") {
            div("w-3/5 h-screen p-4 m-4 flex flex-row gap-2") {
                div("flex flex-col gap-2") {
                    button("p-2 bg-gray-300") {
                        +"Reset"
                    }.clicks.map { } handledBy game.reset

                    button("p-2 bg-gray-300") {
                        +"Kill All"
                    }.clicks.map { } handledBy game.killAll

                    listOf(
                        "Fast" to 1,
                        "Normal" to 2,
                        "Slow" to 4
                    ).forEach { (text, ticks) ->
                        button("p-2 bg-gray-300") {
                            +"Spawn $text Orc"
                        }.clicks.map { ticks } handledBy game.spawnOrc
                    }

                    listOf(
                        "Fast" to 1,
                        "Normal" to 2,
                        "Slow" to 4
                    ).forEach { (text, ticks) ->
                        button("p-2 bg-gray-300") {
                            +"Spawn $text Troll"
                        }.clicks.map { ticks } handledBy game.spawnTroll
                    }

                    input(id = "Tickspeed") {
                        type("range")
                        name("Tickspeed")
                        min("10")
                        max("400")
                        step("20")
                        value(storedTickspeed.data.map { it.toString() })
                        changes.values().map { it.toLong() } handledBy storedTickspeed.update
                    }
                    label {
                        `for`("Tickspeed")
                        storedTickspeed.data.map { "Tickspeed ($it ms)" }.renderText(into = this)
                    }
                }
                div("w-full grid grid-cols-[repeat(40,_minmax(0,_1fr))] justify-items-center") {
                    storedFields.data.renderEach(into = this) { field ->
                        val color = when (field.ground) {
                            Tile.Grass -> if(field.base == Tile.Empty) "bg-green-300" else "bg-yellow-100"
                            Tile.StompedGrass -> if(field.base == Tile.Empty) "bg-green-200" else "bg-yellow-100"
                            Tile.Tree -> "bg-green-600"
                            Tile.Stone -> "bg-gray-500"
                            else -> ""
                        }
                        div("w-full flex justify-center $color") {
                            if (field.base == Tile.Empty) {
                                +field.ground.symbol
                            } else {
                                +field.base.symbol
                            }

                            /*
                            clicks.map { Coordinate.of(50) }
                                .onEach { console.log("Coordinate: $it") } handledBy game.spawn
                             */
                        }
                    }
                }

                tick.combine(storedTickspeed.data, ::Pair).map { (index, speed) ->
                    delay(speed)
                    console.log(index)
                } handledBy game.next
            }
        }
    }
}