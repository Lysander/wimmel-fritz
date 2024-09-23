package app

import dev.fritz2.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import model.*

class GameStore(initial: GameState) : RootStore<GameState>(initial, Job()) {
    val next = handle { old ->
        old.entities.fold(GameState(old.world, emptyList())) { (world, entities), entity ->
            var newEntity = entity.copy(state = entity.state.tick())
            if (newEntity.state.canAct) {
                val newLocation = entity.movement.next(world, entity.coordinate)
                newEntity = newEntity.copy(coordinate = newLocation)
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
                Bouncing(Move.Stay)
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
                KeepOn(Move.Stay)
            )
        )
    }

    val spawnGoblin = handle<Int> { state, ticks ->
        state.copy(
            entities = state.entities + Entity(
                "G${state.entities.size}",
                Tile.Goblin,
                state.world.getStartCoordinate(),
                ActingState(ticks),
                SurroundObject(Move.Up, Move.Left)
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
        GameState(generateInitialWorld(), emptyList())
    }

    val applyCellularAutomata = handle {
        it.copy(world = applyCellularAutomata(it.world))
    }

    val applyErosion = handle {
        it.copy(world = erosion(it.world))
    }

    val applyDilattation = handle {
        it.copy(world = dilattation(it.world))
    }

    val applyRemoveGnubbels = handle {
        it.copy(world = removeGnubbels(it.world))
    }
}

fun main() {
    val game = GameStore(
        GameState(
            erosion(generateCellularWorld(3)),
            emptyList()
        )
    )

    val storedFields = game.map(GameState.world() + World.fields())

    val storedTickspeed = storeOf(100L, Job())
    val tick = generateSequence(1) { it + 1 }.asFlow()
    val storedTickType = storeOf(true, Job())

    render {
        div("w-full h-screen flex items-center justify-center") {
            div("w-screen h-screen p-4 m-4 flex flex-row gap-2") {
                div("grid grid-cols-2 gap-2") {
                    div("flex flex-col gap-2") {
                        button("p-2 bg-gray-300") {
                            +"Kill All"
                        }.clicks.map { } handledBy game.killAll

                        listOf(
                            "Fast" to 1,
                            "Normal" to 2,
                            "Slow" to 4
                        ).forEach { (text, ticks) ->
                            button("p-2 bg-gray-300") {
                                +"$text Orc"
                            }.clicks.map { ticks } handledBy game.spawnOrc
                        }

                        listOf(
                            "Fast" to 1,
                            "Normal" to 2,
                            "Slow" to 4
                        ).forEach { (text, ticks) ->
                            button("p-2 bg-gray-300") {
                                +"$text Troll"
                            }.clicks.map { ticks } handledBy game.spawnTroll
                        }

                        listOf(
                            "Fast" to 1,
                            "Normal" to 2,
                            "Slow" to 4
                        ).forEach { (text, ticks) ->
                            button("p-2 bg-gray-300") {
                                +"$text Goblin"
                            }.clicks.map { ticks } handledBy game.spawnGoblin
                        }

                        input(id = "Tickspeed") {
                            type("range")
                            name("Tickspeed")
                            min("5")
                            max("400")
                            step("10")
                            value(storedTickspeed.data.map { it.toString() })
                            changes.values().map { it.toLong() } handledBy storedTickspeed.update
                        }
                        label {
                            `for`("Tickspeed")
                            storedTickspeed.data.map { "Tickspeed ($it ms)" }.renderText(into = this)
                        }
                    }
                    div("flex flex-col gap-2") {
                        button("p-2 bg-gray-300") {
                            +"Reset"
                        }.clicks.map { } handledBy game.reset

                        button("p-2 bg-gray-300") {
                            +"Next Iteration"
                        }.clicks.map { } handledBy game.applyCellularAutomata

                        button("p-2 bg-gray-300") {
                            +"Erosion"
                        }.clicks.map { } handledBy game.applyErosion

                        button("p-2 bg-gray-300") {
                            +"Dilattation"
                        }.clicks.map { } handledBy game.applyDilattation

                        button("p-2 bg-gray-300") {
                            +"Remove Gnubbels"
                        }.clicks.map { } handledBy game.applyRemoveGnubbels

                        button("p-2 bg-gray-300") {
                            storedTickType.data.map { 
                                if(it) "Move Automatic" else "Move by 'M'" 
                            }.renderText(into = this)
                        }.clicks.map { !storedTickType.current } handledBy storedTickType.update
                    }
                }
                // TODO: Grid von World-Breite ableiten!
                div("w-full h-96 grid grid-cols-[repeat(80,_minmax(0,_1fr))] justify-items-center text-xl") {
                    storedFields.data.renderEach(into = this) { field ->
                        val color = when (field.ground) {
                            Tile.Grass -> when (field.base) { 
                                 Tile.Orc -> "bg-yellow-100" 
                                 Tile.Troll -> "bg-cyan-300" 
                                 Tile.Goblin -> "bg-red-300" 
                                 else -> "bg-green-300"
                            }
                            Tile.StompedGrass ->  when (field.base) { 
                                 Tile.Orc -> "bg-yellow-100" 
                                 Tile.Troll -> "bg-cyan-300" 
                                 Tile.Goblin -> "bg-red-300" 
                                 else -> "bg-green-200"
                            }
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

                combine(
                    tick,
                    storedTickspeed.data,
                    storedTickType.data,
                    ::Triple
                ).mapNotNull { (index, speed, typ) ->
                    if(typ) delay(speed) else null
                } handledBy game.next

                combine(
                    Window.keydownsIf { shortcutOf(this) == shortcutOf("m") },
                    storedTickType.data,
                    ::Pair
                ).map { } handledBy game.next
            }
        }
    }
}