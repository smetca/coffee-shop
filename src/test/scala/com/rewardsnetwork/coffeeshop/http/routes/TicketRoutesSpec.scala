package com.rewardsnetwork.coffeeshop.http.routes

import cats.effect.*
import cats.implicits.*
import cats.effect.testing.scalatest.AsyncIOSpec

import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.http4s.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.UUID

import com.rewardsnetwork.coffeeshop.algebras.*
import com.rewardsnetwork.coffeeshop.domain.*
import com.rewardsnetwork.coffeeshop.domain.ticket.*
import com.rewardsnetwork.coffeeshop.domain.pagination.*
import com.rewardsnetwork.coffeeshop.fixtures.*

class TicketRoutesSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with Http4sDsl[IO]
    with TicketFixture {

  //-----------------------------
  // setup
  //-----------------------------
  val tickets: Tickets[IO] = new Tickets[IO] {
    override def create(ticketInfo: TicketInfo): IO[UUID] =
      IO.pure(NewTicketId)

    override def all(): IO[List[Ticket]] =
      IO.pure(List(OrderTicket))

    override def all(filter: TicketFilter, pagination: Pagination): IO[List[Ticket]] =
      if (filter.completed) IO.pure(List.empty)
      else IO.pure(List(OrderTicket))

    override def find(id: UUID): IO[Option[Ticket]] =
      if (id == TicketId)
        IO.pure(Some(OrderTicket))
      else
        IO.pure(None)

    override def update(id: UUID, ticketInfo: TicketInfo): IO[Option[Ticket]] =
      if (id == TicketId)
        IO.pure(Some(UpdatedTicket))
      else
        IO.pure(None)

    override def delete(id: UUID): IO[Int] =
      if (id == TicketId) IO.pure(1)
      else IO.pure(0)
  }

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  val ticketRoutes: HttpRoutes[IO] = TicketRoutes[IO](tickets).routes

  //-----------------------------
  // tests
  //-----------------------------

  "TicketRoutes" - {
    "should return a ticket with a given id" in {
      for {
        // simulate an HTTP request
        response <- ticketRoutes.orNotFound.run(
          Request(method = Method.GET, uri = uri"/tickets/243df418-ec6e-4d49-9279-f799c0f40064")
        )
        retrieved <- response.as[Ticket]
        // get the HTTP response
        // make some assertions
      } yield {
        response.status shouldBe Status.Ok
        retrieved shouldBe OrderTicket
      }
    }

    "should return all tickets" in {
      for {
        // simulate an HTTP request
        response <- ticketRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/tickets")
            .withEntity(TicketFilter()) // empty filter
        )
        retrieved <- response.as[List[Ticket]]
        // get the HTTP response
        // make some assertions
      } yield {
        response.status shouldBe Status.Ok
        retrieved shouldBe List(OrderTicket)
      }
    }

    "should return all tickets that satisfy a filter" in {
      for {
        // simulate an HTTP request
        response <- ticketRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/tickets")
            .withEntity(TicketFilter(completed = true)) // filter returning nothing
        )
        retrieved <- response.as[List[Ticket]]
        // get the HTTP response
        // make some assertions
      } yield {
        response.status shouldBe Status.Ok
        retrieved shouldBe List()
      }
    }

    "should create a new ticket" in {
      for {
        // simulate an HTTP request
        response <- ticketRoutes.orNotFound.run(
          Request(method = Method.POST, uri = uri"/tickets/create")
            .withEntity(OrderTicket.ticketInfo)
        )
        retrieved <- response.as[UUID]
        // get the HTTP response
        // make some assertions
      } yield {
        response.status shouldBe Status.Created
        retrieved shouldBe NewTicketId
      }
    }

    "should only update a ticket that exists" in {
      for {
        // simulate an HTTP request
        responseOk <- ticketRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/tickets/243df418-ec6e-4d49-9279-f799c0f40064")
            .withEntity(UpdatedTicket.ticketInfo)
        )
        responseInvalid <- ticketRoutes.orNotFound.run(
          Request(method = Method.PUT, uri = uri"/tickets/00000000-0000-0000-0000-000000000000")
            .withEntity(UpdatedTicket.ticketInfo)
        )
        // make some assertions
      } yield {
        responseOk.status shouldBe Status.Ok
        responseInvalid.status shouldBe Status.NotFound
      }
    }

    "should only delete a ticket that exists" in {
      for {
        // simulate an HTTP request
        responseOk <- ticketRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/tickets/243df418-ec6e-4d49-9279-f799c0f40064")
        )
        responseInvalid <- ticketRoutes.orNotFound.run(
          Request(method = Method.DELETE, uri = uri"/tickets/00000000-0000-0000-0000-000000000000")
        )
        // make some assertions
      } yield {
        responseOk.status shouldBe Status.Ok
        responseInvalid.status shouldBe Status.NotFound
      }
    }

  }
}
