package org.example.hello.impl

import play.api.libs.json.Json
import play.api.libs.json.Format
import java.time.LocalDateTime

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.persistence.{
  AggregateEvent,
  AggregateEventTag,
  AkkaTaggerAdapter,
  PersistentEntity
}
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import play.api.libs.json._

import scala.collection.immutable.Seq

class HelloEntity extends PersistentEntity {
  override type Command = HelloCommand
  override type Event = HelloEvent
  override type State = Option[String]

  override def initialState: Option[String] = None

  override def behavior: Behavior =
    Actions()
      .onCommand[UseGreetingMessage, Done] {
        case (createUser: UseGreetingMessage, ctx, _) =>
          ctx.thenPersist(GreetingMessageChanged(createUser.message)) { _ =>
            ctx.reply(Done.done())
          }
      }
      .onReadOnlyCommand[Hello, String] {
        case (_: Hello, ctx, state) => ctx.reply(state.get)
      }
      .onEvent {
        case (event: GreetingMessageChanged, _) =>
          Some(event.message)
      }
}

/**
  * This interface defines all the events that the HelloAggregate supports.
  */
sealed trait HelloEvent extends AggregateEvent[HelloEvent] {
  def aggregateTag: AggregateEventTag[HelloEvent] = HelloEvent.Tag
}

object HelloEvent {
  val Tag: AggregateEventTag[HelloEvent] = AggregateEventTag[HelloEvent]
}

/**
  * An event that represents a change in greeting message.
  */
case class GreetingMessageChanged(message: String) extends HelloEvent

object GreetingMessageChanged {

  /**
    * Format for the greeting message changed event.
    *
    * Events get stored and loaded from the database, hence a JSON format
    * needs to be declared so that they can be serialized and deserialized.
    */
  implicit val format: Format[GreetingMessageChanged] = Json.format
}

/**
  * This is a marker trait for commands.
  * We will serialize them using Akka's Jackson support that is able to deal with the replyTo field.
  * (see application.conf)
  */
trait HelloCommandSerializable

/**
  * This interface defines all the commands that the HelloAggregate supports.
  */
sealed trait HelloCommand extends HelloCommandSerializable

/**
  * A command to switch the greeting message.
  *
  * It has a reply type of [[Confirmation]], which is sent back to the caller
  * when all the events emitted by this command are successfully persisted.
  */
case class UseGreetingMessage(message: String)
    extends HelloCommand
    with ReplyType[Done]

/**
  * A command to say hello to someone using the current greeting message.
  *
  * The reply type is String, and will contain the message to say to that
  * person.
  */
case class Hello(name: String) extends HelloCommand with ReplyType[String]

final case class Greeting(message: String)

object Greeting {
  implicit val format: Format[Greeting] = Json.format
}

sealed trait Confirmation

case object Confirmation {
  implicit val format: Format[Confirmation] = new Format[Confirmation] {
    override def reads(json: JsValue): JsResult[Confirmation] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[Rejected](json)
      else
        Json.fromJson[Accepted](json)
    }

    override def writes(o: Confirmation): JsValue = {
      o match {
        case acc: Accepted => Json.toJson(acc)
        case rej: Rejected => Json.toJson(rej)
      }
    }
  }
}

sealed trait Accepted extends Confirmation

case object Accepted extends Accepted {
  implicit val format: Format[Accepted] =
    Format(Reads(_ => JsSuccess(Accepted)), Writes(_ => Json.obj()))
}

case class Rejected(reason: String) extends Confirmation

object Rejected {
  implicit val format: Format[Rejected] = Json.format
}

/**
  * Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object HelloSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[GreetingMessageChanged],
    // the replies use play-json as well
    JsonSerializer[Greeting],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected]
  )
}
