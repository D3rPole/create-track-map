package littlechasiu.ctm

import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.Navigation
import com.simibubi.create.content.trains.entity.Train
import com.simibubi.create.content.trains.entity.TravellingPoint
import com.simibubi.create.content.trains.graph.TrackEdge
import com.simibubi.create.content.trains.graph.TrackGraph
import com.simibubi.create.content.trains.graph.TrackNode
import com.simibubi.create.content.trains.graph.TrackNodeLocation
import com.simibubi.create.content.trains.schedule.ScheduleEntry
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

fun getInstructions(instructions: List<ScheduleEntry>): ArrayList<ScheduleInstruction> {
  val result: ArrayList<ScheduleInstruction> = ArrayList()

  for(entry in instructions){
    if(entry.instruction is DestinationInstruction){
      result.add(
        ScheduleInstructionDestination(
          stationName = (entry.instruction as DestinationInstruction).summary.second.string,
        )
      )
    }
    if(entry.instruction is ChangeTitleInstruction){
      result.add(
        ScheduleInstructionNameChange(
          newName = (entry.instruction as ChangeTitleInstruction).scheduleTitle,
        )
      )
    }
    if(entry.instruction is ChangeThrottleInstruction){
      result.add(
        ScheduleInstructionThrottleChange(
          throttle = (entry.instruction as ChangeThrottleInstruction).summary.second.string,
        )
      )
    }

  }
  return result
}

val ScheduleRuntime.sendable
  get() = schedule?.let {
    CreateSchedule(
            cycling = it.cyclic,
            instructions = getInstructions(it.entries),
            paused = paused,
            currentEntry = currentEntry,
            arrivalCountdown = 0.0
    )
  }

private fun getNextEdge(graph: TrackGraph, trackNode: TrackNode, trackEdge: TrackEdge, direction: Vec3) : TrackEdge?{
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

private fun pathFromTo(startEdge: TrackEdge, graph: TrackGraph, endNode: TrackNode) : ArrayList<Edge> {
  val result = ArrayList<Edge>()

  var tEdge: TrackEdge = startEdge

  var direction = Vec3(0.0, 0.0, 0.0)
  var reachedEnd = false
  val MAX_PREDICTIONS = 50;

  var j = 0
  while (!reachedEnd) {
    direction = tEdge.getDirection(false)
    tEdge = getNextEdge(graph, tEdge.node2, tEdge, direction) ?: return result

    j++
    if (tEdge.node1.netId == endNode.netId) {
      reachedEnd = true // incase tEdge is the next scheduled edge
    } else {
      result.add(tEdge.sendable as Edge)
    }

    if (tEdge.node2.netId == endNode.netId || j > MAX_PREDICTIONS) {
      reachedEnd = true
    }
  }
  return result
}

private fun getEdgeFromStation(station: GlobalStation, graph: TrackGraph) : TrackEdge {
  val firstNode = graph.locateNode(station.edgeLocation.first)
  val secondNode = graph.locateNode(station.edgeLocation.second)
  return graph.getConnection(Couple.create(firstNode, secondNode))
}

private fun getCurrentTrainPath(navigation: Navigation?) : Pair< List<Edge>, List<Edge> >{
  val graph = navigation?.train?.graph
  val result : ArrayList<Edge> = ArrayList()
  val debug : ArrayList<Edge> = ArrayList()
  if(navigation == null || graph == null || navigation.destination == null){
    return Pair(result, debug)
  }
  val field = Navigation::class.java.getDeclaredField("currentPath")
  field.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  val currentPath = field.get(navigation) as List<Couple<TrackNode>>

  val firstEdge: TrackEdge = graph.getConnection(navigation.train.endpointEdges.first)
  val lastEdge: TrackEdge = getEdgeFromStation(navigation.destination, graph)
  if(firstEdge == lastEdge){
    return Pair(result, debug)
  }

  result.add(firstEdge.sendable as Edge)
  result.add(lastEdge.sendable as Edge)
  if(currentPath.size > 0){
    result.addAll(pathFromTo(firstEdge, graph, currentPath[0].first))
    result.addAll(pathFromTo(graph.getConnection(currentPath[currentPath.size - 1]), graph, lastEdge.node1))
  }else{
    result.addAll(pathFromTo(firstEdge, graph, lastEdge.node1))
  }

  currentPath.forEachIndexed{i, obj ->
    val trackEdge : TrackEdge = graph.getConnectionsFrom(obj.first).get(obj.second) ?: return@forEachIndexed
    val edge = trackEdge.sendable
    if(edge !is Edge) {return@forEachIndexed}
    result.add(edge)

    if(i < currentPath.size - 1){
      result.addAll(pathFromTo(trackEdge, graph, currentPath[i + 1].first))
    }
  }
  return Pair(result, debug)
}

val Train.sendable: CreateTrain
  get() {
    val (currentPath, debug) = getCurrentTrainPath(navigation)
    return CreateTrain(
            id = id,
            name = name.string,
            owner = null,
            cars = carriages.map { it.sendable }.toList(),
            speed = speed,
            backwards = speed < 0,
            stopped = speed == 0.0,
            schedule = runtime.sendable,
            currentPath = currentPath,
            debug = debug
    )
  }
