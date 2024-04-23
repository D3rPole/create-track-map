package littlechasiu.ctm.model

import com.simibubi.create.content.trains.entity.Navigation
import com.simibubi.create.content.trains.schedule.Schedule
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

object UUIDSerializer : KSerializer<UUID> {
  override val descriptor =
    PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): UUID {
    return UUID.fromString(decoder.decodeString())
  }

  override fun serialize(encoder: Encoder, value: UUID) {
    encoder.encodeString(value.toString())
  }
}

@Serializable
data class Point(
  val x: Double,
  val y: Double,
  val z: Double,
)

@Serializable
data class Path(
  val start: Point,
  val firstControlPoint: Point,
  val secondControlPoint: Point,
  val end: Point,
)

@Serializable
data class Edge(
  val dimension: String,
  val path: List<Point>,
)

@Serializable
data class DimensionLocation(
  val dimension: String,
  val location: Point
)

@Serializable
data class Portal(
  val from: DimensionLocation,
  val to: DimensionLocation,
)

@Serializable
data class Station(
  @Serializable(with = UUIDSerializer::class)
  val id: UUID,
  val name: String,
  val dimension: String,
  val location: Point,
  val angle: Double,
  val assembling: Boolean,
)

@Serializable
data class SignalSide(
  val type: SignalType,
  val state: SignalState,
  val angle: Double,
  @Serializable(with = UUIDSerializer::class)
  val block: UUID?,
)

@Serializable
data class Network(
  val tracks: List<Edge>,
  val portals: List<Portal>,
  val stations: List<Station>,
)

@Serializable
data class Signal(
  @Serializable(with = UUIDSerializer::class)
  val id: UUID,
  val dimension: String,
  val location: Point,
  val forward: SignalSide?,
  val reverse: SignalSide?,
)

@Serializable
data class SignalStatus(
  val signals: List<Signal>,
)

@Serializable
data class Block(
  @Serializable(with = UUIDSerializer::class)
  val id: UUID,
  val occupied: Boolean,
  val reserved: Boolean,
  val segments: List<Edge>,
)

@Serializable
data class BlockStatus(
  val blocks: List<Block>
)

@Serializable
data class TrainCar(
  val id: Int,
  val leading: DimensionLocation? = null,
  val trailing: DimensionLocation? = null,
  val portal: Portal? = null,
)

@Serializable
sealed class ScheduleInstruction(
    val instructionType: String,
)
@Serializable
data class ScheduleInstructionDestination(
    val stationName : String,
) : ScheduleInstruction(instructionType = "Destination")

@Serializable
data class ScheduleInstructionThrottleChange(
    val throttle : String,
) : ScheduleInstruction(instructionType = "ThrottleChange")

@Serializable
data class ScheduleInstructionNameChange(
    val newName : String,
) : ScheduleInstruction(instructionType = "NameChange")

@Serializable
data class CreateSchedule(
  val instructions: List<ScheduleInstruction>,
  val cycling: Boolean,
  val paused: Boolean,
  val currentEntry: Int,
)

@Serializable
data class CreateTrain(
  @Serializable(with = UUIDSerializer::class)
  val id: UUID,
  val name: String,
  val owner: String?,
  val cars: List<TrainCar>,
  val backwards: Boolean,
  val stopped: Boolean,
  val speed: Double,
  val schedule: CreateSchedule?,
)

@Serializable
data class TrainStatus(
  val trains: List<CreateTrain>,
)
