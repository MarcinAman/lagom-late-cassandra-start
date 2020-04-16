package org.example.hello.impl

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.scaladsl.persistence.{
  AggregateEventTag,
  EventStreamElement,
  ReadSideProcessor
}

class HelloProcessor extends ReadSideProcessor[HelloEvent] {
  override def buildHandler(): ReadSideProcessor.ReadSideHandler[HelloEvent] = {
    new ReadSideProcessor.ReadSideHandler[HelloEvent] {
      override def handle()
        : Flow[EventStreamElement[HelloEvent], Done, NotUsed] = {
        Flow[EventStreamElement[HelloEvent]].map { eventElement =>
          println(s"------------- HelloProcessor: $eventElement -------------")
          Done.done()
        }
      }
    }
  }

  override def aggregateTags: Set[AggregateEventTag[HelloEvent]] =
    Set(HelloEvent.Tag)
}
