package model

import dev.fritz2.core.Lenses
import kotlin.random.Random


data class Coordinate(val x: Int, val y: Int) {
    companion object {
        fun of(index: Int) = Coordinate(index % World.MAX_Y, index / World.MAX_Y)
    }

    val index: Int = y * World.MAX_X + x

    operator fun plus(move: Move) = Coordinate(x + move.x, y + move.y)
}

enum class Move(val x: Int, val y: Int) {
    Stay(0, 0),
    Up(0, -1),
    UpRight(1, -1),
    UpLeft(-1, -1),
    Down(0, 1),
    DownRight(1, 1),
    DownLeft(-1, 1),
    Right(1, 0),
    Left(-1, 0),
}

enum class Tile(
    val symbol: String,
) {
    Empty(""),
    Grass("."),
    StompedGrass("."),
    Stone("#"),
    Tree("*"),
    Orc("O"),
    Troll("T")
}

interface Movement {
    fun next(world: World, current: Coordinate, last: Move): Move
}

class Boucing : Movement {
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

    override fun next(world: World, current: Coordinate, last: Move): Move =
        priorities[last]!!.random().fold(false to last) { (possible, result), move ->
            if (possible) {
                true to result
            } else {
                if (world.isPassable(current + move)) true to move
                else possible to result
            }
        }.let { (possible, move) -> if (possible) move else Move.Stay }

}

class KeepOn : Movement {
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

    override fun next(world: World, current: Coordinate, last: Move): Move =
        prioritiesFor(last).fold(false to last) { (possible, result), move ->
            if (possible) {
                true to result
            } else {
                if (world.isPassable(current + move)) true to move
                else possible to result
            }
        }.let { (possible, move) -> if (possible) move else Move.Stay }

}

class SurroundObject : Movement {

    override fun next(world: World, current: Coordinate, last: Move): Move {
        TODO("Not yet implemented")
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
    val last: Move,
    val movement: Movement
) {
    fun moved(move: Move) = copy(
        coordinate = coordinate + move,
        last = move
    )
}

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
        const val MAX_X: Int = 40
        const val MAX_Y: Int = 25

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
    val entities: List<Entity>
) {
    companion object
}

fun generateWorld(): World {
    val fields = generateSequence {
        when (Random.nextInt(0, 32)) {
            5, 6, 7 -> Tile.Tree
            14, 15 -> Tile.Stone
            else -> Tile.Grass
        }
    }.take(World.MAX_Y * World.MAX_X).map { Field.of(it) }.toList()
    return World(fields)
}

fun generateGrassWorld(): World =
    World(generateSequence { Tile.Grass }.take(World.MAX_Y * World.MAX_X).map { Field.of(it) }.toList())

//fun generateOrganicWorld(): World = generateGrassWorld().copy(fields = )

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
