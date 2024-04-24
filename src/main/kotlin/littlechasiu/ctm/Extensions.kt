package littlechasiu.ctm

import com.simibubi.create.content.trains.entity.Carriage
import com.simibubi.create.content.trains.entity.Navigation
import com.simibubi.create.content.trains.entity.Train
import com.simibubi.create.content.trains.entity.TravellingPoint
import com.simibubi.create.content.trains.graph.TrackEdge
import com.simibubi.create.content.trains.graph.TrackNode
import com.simibubi.create.content.trains.graph.TrackNodeLocation
import com.simibubi.create.content.trains.schedule.ScheduleEntry
import com.simibubi.create.content.trains.schedule.ScheduleRuntime
import com.simibubi.create.content.trains.schedule.destination.ChangeThrottleInstruction
import com.simibubi.create.content.trains.schedule.destination.ChangeTitleInstruction
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction
import com.simibubi.create.foundation.utility.Couple
import littlechasiu.ctm.model.*
import net.minecraft.core.Vec3i
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
    )
  }

fun getCurrentTrainPath(navigation: Navigation?) : List<Edge>{
  val result : ArrayList<Edge> = ArrayList()
  if(navigation == null){
    return result
  }
  val field = Navigation::class.java.getDeclaredField("currentPath")
  field.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  val currentPath = field.get(navigation) as List<Couple<TrackNode>>

  currentPath.forEach{
    val trackEdge : TrackEdge = navigation.train.graph.getConnectionsFrom(it.first).get(it.second) ?: return@forEach
    val edge = trackEdge.sendable
    if(edge is Edge)
    result.add(edge)
  }
  return result
}

val Train.sendable
  get() =
    CreateTrain(
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
