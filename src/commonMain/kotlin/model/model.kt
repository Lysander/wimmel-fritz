package model

import dev.fritz2.core.Lenses
import kotlin.random.Random


data class Coordinate(val x: Int, val y: Int) {
    companion object {
        fun of(index: Int) = Coordinate(index % World.MAX_X, index / World.MAX_X)
    }

    val index: Int = y * World.MAX_X + x

    operator fun plus(move: Move) = Coordinate(x + move.x, y + move.y)
}

/**
 * ```
 *  01234567
 * 1........
 * 2....x...
 * 3........
 * 4........
 * ```
 */
enum class Move(val x: Int, val y: Int) {
    Stay(0, 0),
    Up(0, -1),
    UpRight(1, -1),
    UpLeft(-1, -1),
    Down(0, 1),
    DownRight(1, 1),
    DownLeft(-1, 1),
    Right(1, 0),
    Left(-1, 0);

    fun reverse(): Move = when (this) {
        Stay -> Stay
        Up -> Down
        Down -> Up
        Left -> Right
        Right -> Left
        else -> Stay
    }

    fun orthogonal(): List<Move> = when (this) {
        Up, Down -> listOf(Right, Left)
        Right, Left -> listOf(Up, Down)
        else -> listOf(Stay)
    }

}

val neighbours: List<Move> = Move.values().filterNot { it == Move.Stay }.toList()
val neighboursFour: List<Move> = listOf(Move.Up, Move.Down, Move.Right, Move.Left)

enum class Tile(
    val symbol: String,
) {
    Empty(""),
    Grass(";"),
    StompedGrass("."),
    Stone("^"),
    Tree("*"),
    Orc("O"),
    Troll("T"),
    Goblin("G"),
    Mimic("M"),
}

interface Movement {
    fun next(world: World, current: Coordinate): Coordinate
}

class Bouncing(private var last: Move) : Movement {
    private val priorities: Map<Move, List<List<Move>>> = mapOf(
        Move.Up to (
                listOf(
                    listOf(Move.Up, Move.Right, Move.Left, Move.Down),
                    listOf(Move.Up, Move.Left, Move.Right, Move.Down)
                )),
        Move.Down to (
                listOf(
                    listOf(Move.Down, Move.Right, Move.Left, Move.Up),
                    listOf(Move.Down, Move.Left, Move.Right, Move.Up)
                )),
        Move.Right to (
                listOf(
                    listOf(Move.Right, Move.Up, Move.Down, Move.Left),
                    listOf(Move.Right, Move.Down, Move.Up, Move.Left)
                )),
        Move.Left to (
                listOf(
                    listOf(Move.Left, Move.Up, Move.Down, Move.Right),
                    listOf(Move.Left, Move.Down, Move.Up, Move.Right)
                )),
        Move.Stay to (
                listOf(
                    listOf(Move.Up, Move.Down, Move.Right, Move.Left)
                )),
    )

    override fun next(world: World, current: Coordinate): Coordinate =
        priorities[last]!!.random().fold(false to last) { (possible, result), move ->
            if (possible) {
                true to result
            } else {
                if (world.isPassable(current + move)) true to move
                else possible to result
            }
        }.let { (possible, move) -> if (possible) move else Move.Stay }
            .also { last = it }
            .let { current + it }

}

class KeepOn(private var last: Move) : Movement {
    private val priorities = mapOf(
        Move.Up to buildList {
            add(listOf(Move.Up, Move.UpLeft, Move.UpRight))
            add(listOf(Move.Left, Move.Right))
            add(listOf(Move.Down, Move.DownLeft, Move.DownRight))
        },
        Move.Down to buildList {
            add(listOf(Move.Down, Move.DownLeft, Move.DownRight))
            add(listOf(Move.Left, Move.Right))
            add(listOf(Move.Up, Move.UpLeft, Move.UpRight))
        },
        Move.Right to buildList {
            add(listOf(Move.Right, Move.UpRight, Move.DownRight))
            add(listOf(Move.Up, Move.Down))
            add(listOf(Move.Left, Move.UpLeft, Move.DownLeft))
        },
        Move.Left to buildList {
            add(listOf(Move.Left, Move.UpLeft, Move.DownLeft))
            add(listOf(Move.Up, Move.Down))
            add(listOf(Move.Right, Move.UpRight, Move.DownRight))
        },
        Move.UpRight to buildList {
            add(listOf(Move.Up, Move.UpRight, Move.Right))
            add(listOf(Move.UpLeft, Move.DownRight))
            add(listOf(Move.Down, Move.DownLeft, Move.Left))
        },
        Move.DownLeft to buildList {
            add(listOf(Move.Down, Move.DownLeft, Move.Left))
            add(listOf(Move.UpLeft, Move.DownRight))
            add(listOf(Move.Up, Move.UpRight, Move.Right))
        },
        Move.UpLeft to buildList {
            add(listOf(Move.Up, Move.UpLeft, Move.Left))
            add(listOf(Move.UpRight, Move.DownLeft))
            add(listOf(Move.Down, Move.DownRight, Move.Right))
        },
        Move.DownRight to buildList {
            add(listOf(Move.Down, Move.DownRight, Move.Right))
            add(listOf(Move.UpRight, Move.DownLeft))
            add(listOf(Move.Up, Move.UpLeft, Move.Left))
        },
        Move.Stay to listOf(Move.values().filter { it != Move.Stay })
    )

    private fun prioritiesFor(move: Move) = priorities[move]!!.flatMap { it.shuffled() }

    override fun next(world: World, current: Coordinate): Coordinate =
        prioritiesFor(last).fold(false to last) { (possible, result), move ->
            if (possible) {
                true to result
            } else {
                if (world.isPassable(current + move)) true to move
                else possible to result
            }
        }.let { (possible, move) -> if (possible) move else Move.Stay }
            .also { last = it }
            .let { current + it }

}

class SurroundObject(private var lastMove: Move, private var lastWallDirection: Move) : Movement {
    private val initialMovement: Movement = Bouncing(lastMove)
    private var lastMovements: List<Move> = emptyList()

    private enum class State {
        Searching,
        Surrounding
    }

    private var state = State.Searching

    private fun surroundEmptySpace() = lastMovements.toHashSet().size == 4

    private fun pushMove(move: Move) {
        lastMovements = if(lastMovements.size == 4) lastMovements.drop(1) + move else lastMovements + move
    }

    override fun next(world: World, current: Coordinate): Coordinate {
        if(state == State.Surrounding && surroundEmptySpace()) {
            println("Gehe zurück auf Suchmodus")
            state = State.Searching
            lastMovements = emptyList()
        }
        return if (state == State.Searching) {
            val nextPosition = initialMovement.next(world, current)
            if (current + lastMove == nextPosition) nextPosition
            else {
                lastWallDirection = lastMove
                lastMove = lastWallDirection.orthogonal().shuffled().first()
                state = State.Surrounding
                nextBySurrounding(world, current)
            }
        } else {
            nextBySurrounding(world, current)
        }
    }

    private fun nextBySurrounding(world: World, current: Coordinate): Coordinate =
        if (world.isPassable(current + lastMove) && !world.isPassable(current + lastWallDirection)) {
            pushMove(lastMove)
            current + lastMove
        } else if (world.isPassable(current + lastWallDirection)) {
            val tmpLastMove = lastMove
            lastMove = lastWallDirection
            pushMove(lastMove)
            lastWallDirection = tmpLastMove.reverse()
            current + lastMove
        } else if (!world.isPassable(current + lastMove) && world.isPassable(current + lastWallDirection.reverse())) {
            val tmpLastWallDir = lastWallDirection
            lastWallDirection = lastMove
            lastMove = tmpLastWallDir.reverse()
            pushMove(lastMove)
            current + tmpLastWallDir.reverse()
        } else {
            lastMove = lastMove.reverse()
            pushMove(lastMove)
            lastWallDirection = lastWallDirection.reverse()
            current + lastMove
        }
}

class SwitchingMovement(private var movement: Movement) : Movement {
    private var countedMoves = 0
    private val movements = listOf(
        Bouncing(Move.Up),
        KeepOn(Move.Right),
        SurroundObject(Move.Left, Move.Right)
    )
    
    override fun next(world: World, current: Coordinate): Coordinate {
        countedMoves += 1
        if(countedMoves > 100) {
            countedMoves = 0
            movement = movements.shuffled().first()
        }
        return movement.next(world, current)
    }
}

data class ActingState(
    val ticks: Int,
    val current: Int = 1
) {
    val canAct: Boolean = ticks == current

    fun tick() = ActingState(ticks, if (current < ticks) current + 1 else 1)
}

data class Entity(
    val id: String,
    val tile: Tile,
    val coordinate: Coordinate,
    val state: ActingState,
    val movement: Movement
)

data class Field(
    val ground: Tile,
    val base: Tile,
) {
    companion object {
        fun of(tile: Tile) = Field(ground = tile, base = Tile.Empty)

        fun ofSymbols(symbols: String): List<Field> = symbols.map {
            of(
                when (it) {
                    '.' -> Tile.Grass
                    '#' -> Tile.Stone
                    '*' -> Tile.Tree
                    else -> Tile.Empty
                }
            )
        }
    }
}

@Lenses
data class World(
    val fields: List<Field>
) {
    companion object {
        const val MAX_X: Int = 80
        const val MAX_Y: Int = 25

        const val size: Int = MAX_X * MAX_Y
    }

    fun getStartCoordinate(): Coordinate =
        Coordinate.of(fields.withIndex().filter { (_, field) -> field.base == Tile.Empty && field.ground.ordinal < 3 }
            .shuffled().first().index)

    fun inBounds(coordinate: Coordinate) =
        coordinate.x >= 0 && coordinate.x < MAX_X && coordinate.y >= 0 && coordinate.y < MAX_Y

    fun isPassable(coordinate: Coordinate) = if (inBounds(coordinate)) {
        fields[coordinate.index].let {
            it.base == Tile.Empty && (it.ground == Tile.Empty || it.ground == Tile.Grass || it.ground == Tile.StompedGrass)
            //it.base == Tile.Empty && (it.ground == Tile.Empty || it.ground == Tile.Grass)
        }
    } else false

    fun update(old: Entity, new: Entity): World = World(fields.mapIndexed { index, field ->
        if (new.coordinate.index == index) field.copy(base = new.tile)
        else if (old.coordinate.index == index) field.copy(
            ground = if (field.ground == Tile.Grass) Tile.StompedGrass else field.ground,
            base = Tile.Empty
        )
        else field
    })

    fun clearEntities() =
        World(fields.map { it.copy(if (it.ground == Tile.StompedGrass) Tile.Grass else it.ground, Tile.Empty) })
}

@Lenses
data class GameState(
    val world: World,
    val entities: List<Entity>,
) {
    companion object
}

fun generateWorld(): World {
    val fields = generateSequence {
        when (Random.nextInt(0, 32)) {
            5, 6, 7, 8 -> Tile.Tree
            14 -> Tile.Stone
            else -> Tile.Grass
        }
    }.take(World.MAX_Y * World.MAX_X).map { Field.of(it) }.toList()
    return World(fields)
}

fun generateGrassWorld(): World =
    World(generateSequence { Tile.Grass }.take(World.MAX_Y * World.MAX_X).map { Field.of(it) }.toList())

fun neighboursOf(index: Int): List<Int> = with(Coordinate.of(index)) {
    neighbours
        .map { this + it }
        .filter { it.x >= 0 && it.x < World.MAX_X && it.y >= 0 && it.y < World.MAX_Y }
        .map { it.index }
}

fun neighboursFourOf(index: Int): List<Int> = with(Coordinate.of(index)) {
    neighboursFour
        .map { this + it }
        .filter { it.x >= 0 && it.x < World.MAX_X && it.y >= 0 && it.y < World.MAX_Y }
        .map { it.index }
}

fun blockedNeighboursOf(index: Int, fields: List<Field>): Int =
    neighboursOf(index).let {
        (neighbours.size - it.size) + it
            .map { fields[it] }
            .filter { it.ground != Tile.Grass }
            .size
    }

fun applyCellularAutomata(world: World): World = world.copy(fields = world.fields.mapIndexed { index, _ ->
    Field.of(if (blockedNeighboursOf(index, world.fields) > 4) Tile.Stone else Tile.Grass)
})

fun generateInitialWorld(): World {
    val indexOfStones = (0 until World.size).shuffled().take((World.size * 0.55).toInt()).toHashSet()
    return generateGrassWorld().let {
        it.copy(fields = it.fields.withIndex().map { (index, field) ->
            if (indexOfStones.contains(index)) Field.of(Tile.Stone) else field
        })
    }
}

fun generateCellularWorld(times: Int): World {
    val initial = generateInitialWorld()
    return (0 until times).fold(initial) { world, _ ->
        applyCellularAutomata(world)
    }
}

fun erosion(world: World): World {
    return world.copy(fields = world.fields.withIndex().map { (index, field) ->
        if(field.ground != Tile.Grass && neighboursOf(index).any { world.fields[it].ground == Tile.Grass }) Field.of(Tile.Grass) else field
    })
}

fun dilattation(world: World): World {
    return world.copy(fields = world.fields.withIndex().map { (index, field) ->
        if(field.ground == Tile.Grass && neighboursOf(index).any { world.fields[it].ground != Tile.Grass }) Field.of(Tile.Stone) else field
    })
}

fun removeGnubbels(world: World): World {
    return world.copy(fields = world.fields.withIndex().map { (index, field) ->
        if(field.ground != Tile.Grass && neighboursFourOf(index).filter { world.fields[it].ground == Tile.Grass }.count() > 2) Field.of(Tile.Grass) else field
    })
}

// 16 x 16
fun getDemoWorld() = World(
    buildList {
        addAll(Field.ofSymbols(".....##........."))
        addAll(Field.ofSymbols(".....#......####"))
        addAll(Field.ofSymbols(".....#.........."))
        addAll(Field.ofSymbols("................"))
        addAll(Field.ofSymbols(".......###......"))
        addAll(Field.ofSymbols("*.....#####....."))
        addAll(Field.ofSymbols("......####......"))
        addAll(Field.ofSymbols("................"))
        addAll(Field.ofSymbols(".......**......."))
        addAll(Field.ofSymbols("......*****....."))
        addAll(Field.ofSymbols("....***...****.."))
        addAll(Field.ofSymbols(".....**..***...."))
        addAll(Field.ofSymbols("..........****.."))
        addAll(Field.ofSymbols(".......#........"))
        addAll(Field.ofSymbols(".......###......"))
        addAll(Field.ofSymbols(".......#.###...."))
    }
)
