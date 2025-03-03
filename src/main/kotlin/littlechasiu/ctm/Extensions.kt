package littlechasiu.ctm

import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.Navigation
import com.simibubi.create.content.trains.entity.Train
import com.simibubi.create.content.trains.entity.TravellingPoint
import com.simibubi.create.content.trains.graph.TrackEdge
import com.simibubi.create.content.trains.graph.TrackGraph
import com.simibubi.create.content.trains.graph.TrackNode
import com.simibubi.create.content.trains.graph.TrackNodeLocation
import com.simibubi.create.content.trains.schedule.ScheduleRuntime
import com.simibubi.create.content.trains.schedule.destination.ChangeThrottleInstruction
import com.simibubi.create.content.trains.schedule.destination.ChangeTitleInstruction
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction
import com.simibubi.create.content.trains.station.GlobalStation
import com.simibubi.create.foundation.utility.Couple
import littlechasiu.ctm.model.*
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

fun <T> MutableSet<T>.replaceWith(other: Collection<T>) {
  this.retainAll { other.contains(it) }
  this.addAll(other)
}

operator fun <T> Couple<T>.component1(): T = this.get(true)
operator fun <T> Couple<T>.component2(): T = this.get(false)

val Vec3.sendable: Point
  get() =
    Point(x = x, y = y, z = z)

val ResourceKey<Level>.string: String
  get() {
    val loc = location()
    return "${loc.namespace}:${loc.path}"
  }

val TrackNodeLocation.sendable get() = location.sendable

val TrackEdge.path: Track
  get() =
    if (isTurn) BezierCurve.from(turn, node1.location.dimension.string)
    else Line(
      node1.location.dimension.string,
      node1.location.location, node2.location.location
    )

val TrackNode.dimensionLocation: DimensionLocation
  get() =
    DimensionLocation(location.dimension.string, location.sendable)

@Suppress("IMPLICIT_CAST_TO_ANY")
val TrackEdge.sendable
  get() =
    if (isInterDimensional)
      Portal(
        from = node1.dimensionLocation,
        to = node2.dimensionLocation)
    else
      path.sendable

val TravellingPoint.sendable
  get() =
    if (node1 == null || edge == null) null else
    DimensionLocation(
      dimension = node1.location.dimension.string,
      location = getPosition(null).sendable,
    )

val Carriage.sendable
  get() =
    TrainCar(
      id = id,
      leading = leadingPoint?.sendable,
      trailing = trailingPoint?.sendable,
      portal = this.train.occupiedSignalBlocks.keys.map {
        TrackMap.watcher.portalsInBlock(it)
      }.flatten().firstOrNull {
        it.from.dimension == leadingPoint?.node1?.location?.dimension?.string &&
          it.to.dimension == trailingPoint?.node1?.location?.dimension?.string
      },
    )

val ScheduleRuntime.sendable
  get() = schedule?.let {
    val field = ScheduleRuntime::class.java.getDeclaredField("ticksInTransit")
    field.isAccessible = true
    val currentTime = field.get(this) as Int


    CreateSchedule(
      cycling = it.cyclic,
      instructions = getInstructions(this),
      paused = paused,
      currentEntry = currentEntry,
      ticksInTransit = currentTime,
    )
  }

/**
 * gets schedule instructions for a train
 * @param scheduleRuntime ScheduleRuntime object associated with the Train
 * @return ArrayList<ScheduleInstruction> result
 */
fun getInstructions(scheduleRuntime: ScheduleRuntime): ArrayList<ScheduleInstruction> {
  val result: ArrayList<ScheduleInstruction> = ArrayList()
  val instructions = scheduleRuntime.schedule.entries

  val field = ScheduleRuntime::class.java.getDeclaredField("predictionTicks")
  field.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  val predictionTicks = field.get(scheduleRuntime) as List<Int>

  instructions.forEachIndexed { i, entry ->
    when (val instruction = entry.instruction) {
      is DestinationInstruction -> {
        val stationName = instruction.summary.second.string
        result.add(ScheduleInstructionDestination(stationName = stationName, ticksToComplete = predictionTicks[i]))
      }
      is ChangeTitleInstruction -> {
        val newName = instruction.scheduleTitle
        result.add(ScheduleInstructionNameChange(newName = newName))
      }
      is ChangeThrottleInstruction -> {
        val throttle = instruction.summary.second.string
        result.add(ScheduleInstructionThrottleChange(throttle = throttle))
      }
    }
  }
  return result
}

val Train.sendable: CreateTrain
  get() {
    return CreateTrain(
            id = id,
            name = name.string,
            owner = null,
            cars = carriages.map { it.sendable }.toList(),
            speed = speed,
            backwards = speed < 0,
            stopped = speed == 0.0,
            schedule = runtime.sendable,
            currentPath = getCurrentTrainPath(navigation),
    )
  }

/*Path calculation methods*/

/**
 * Calculates next TrackEdge when the train isn't turning.
 * @param graph TrackGraph the train is currently on.
 * @param trackEdge TrackEdge the train is currently on.
 * @param trackNode TrackNode the train is driving towards
 * @param direction Vec3 direction where the track / train is pointing
 * @return TrackEdge result
 */
private fun getNextEdge(graph: TrackGraph, trackEdge: TrackEdge, trackNode: TrackNode, direction: Vec3) : TrackEdge?{
  var result : TrackEdge? = null
  var biggest = Double.MIN_VALUE
  graph.getConnectionsFrom(trackNode).forEach { key, value ->
    if (key == trackEdge) {
      return@forEach
    }
    val dot = value.getDirection(true).dot(direction)
    if(dot > biggest && dot > 0){
      biggest = dot
      result = value
    }
  }
  return result
}

/**
 * Calculates path when the train keeps going without turning.
 * This is not a pathfinder endNode must be reachable with no turns.
 * Method stops after 100 iterations by itself
 * @param startEdge TrackEdge start point
 * @param endNode TrackEdge end point
 * @param graph TrackGraph the train is currently on
 * @return ArrayList<Edge> result
 */
private fun straightLinePathToEndNode(startEdge: TrackEdge, endNode: TrackNode, graph: TrackGraph) : ArrayList<Edge> {
  val result = ArrayList<Edge>()

  var tEdge: TrackEdge = startEdge

  var direction: Vec3
  var reachedEnd = false
  val MAX_PREDICTIONS = 100

  var j = 0
  while (!reachedEnd) {
    direction = tEdge.getDirection(false)
    tEdge = getNextEdge(graph, tEdge, tEdge.node2, direction) ?: return result

    j++
    if (tEdge.node1.netId == endNode.netId) {
      reachedEnd = true // incase tEdge is the next scheduled edge
    } else {
      val edge = tEdge.sendable
      if(edge is Edge) { // incase edge is portal, then ignore it
        result.add(edge)
      }
    }

    if (tEdge.node2.netId == endNode.netId || j > MAX_PREDICTIONS) {
      reachedEnd = true
    }
  }
  return result
}

/**
 * Gets the TrackEdge a train station
 * @param station GlobalStation train station
 * @param graph TrackGraph the train station is on
 * @return TrackEdge result
 */
private fun getEdgeFromStation(station: GlobalStation, graph: TrackGraph) : TrackEdge {
  val firstNode = graph.locateNode(station.edgeLocation.first)
  val secondNode = graph.locateNode(station.edgeLocation.second)
  return graph.getConnection(Couple.create(firstNode, secondNode))
}

/**
 * Calculates the current path the train is taking
 * @param navigation Navigation object of Train
 * @return Path information object
 */
private fun getCurrentTrainPath(navigation: Navigation?) : Path{
  val graph = navigation?.train?.graph
  val result : ArrayList<Edge> = ArrayList()
  if(!CreateTrain.enableNavigationTracks || navigation == null || graph == null || navigation.destination == null){
    return Path(result,0.0,0.0)
  }
  val field = Navigation::class.java.getDeclaredField("currentPath")
  field.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  val currentPath = field.get(navigation) as List<Couple<TrackNode>>

  val endPointEdge = if(navigation.train.currentlyBackwards) navigation.train.endpointEdges.second else navigation.train.endpointEdges.first
  val firstNode = endPointEdge.first
  val secondNode = endPointEdge.second
  val firstEdge: TrackEdge = if(navigation.train.currentlyBackwards)
                              graph.getConnection(Couple.create(secondNode, firstNode)) // get the "inverted" edge when driving backwards
                              else
                              graph.getConnection(Couple.create(firstNode, secondNode))

  val lastEdge: TrackEdge = getEdgeFromStation(navigation.destination, graph)
  if(firstEdge == lastEdge){
    return Path(result,0.0,0.0)
  }

  result.add(firstEdge.sendable as Edge)
  result.add(lastEdge.sendable as Edge)
  if(currentPath.isNotEmpty()){
    result.addAll(straightLinePathToEndNode(firstEdge, currentPath[0].first, graph))
    result.addAll(straightLinePathToEndNode(graph.getConnection(currentPath[currentPath.size - 1]), lastEdge.node1, graph))
  }else{
    result.addAll(straightLinePathToEndNode(firstEdge, lastEdge.node1, graph))
  }

  currentPath.forEachIndexed{i, obj ->
    val trackEdge : TrackEdge = graph.getConnectionsFrom(obj.first).get(obj.second) ?: return@forEachIndexed
    val edge = trackEdge.sendable
    if(edge !is Edge) {return@forEachIndexed}
    result.add(edge)

    if(i < currentPath.size - 1){
      result.addAll(straightLinePathToEndNode(trackEdge, currentPath[i + 1].first, graph))
    }
  }
  return Path(result,navigation.distanceStartedAt,navigation.distanceToDestination)
}
