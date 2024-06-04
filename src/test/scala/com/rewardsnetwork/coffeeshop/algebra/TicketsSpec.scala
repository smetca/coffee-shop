package com.rewardsnetwork.coffeeshop.algebra

import cats.effect.*
import cats.implicits.*
import cats.effect.implicits.*
import cats.effect.testing.scalatest.AsyncIOSpec

import doobie.implicits.*
import doobie.util.*
import doobie.postgres.implicits.*
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import com.rewardsnetwork.coffeeshop.algebras.LiveTickets
import com.rewardsnetwork.coffeeshop.domain.ticket.*
import com.rewardsnetwork.coffeeshop.domain.pagination.*
import com.rewardsnetwork.coffeeshop.fixtures.TicketFixture

class TicketsSpec
    extends AsyncFreeSpec
    with AsyncIOSpec
    with Matchers
    with DoobieSpec
    with TicketFixture {
  override val initScript: String = "sql/tickets.sql"

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  "Tickets 'algebra'" - {
    "should not return a ticket if UUID does not exist" in {
      transactor.use { xa =>
        val program = for {
          tickets      <- LiveTickets[IO](xa)
          retrieved <- tickets.find(NotFoundTicketId)
        } yield retrieved

        program.asserting(_ shouldBe None)
      }
    }

    "should return a ticket given an id" in {
      transactor.use { xa =>
        val program = for {
          tickets <- LiveTickets[IO](xa)
          retrieved <- tickets.find(TicketId)
        } yield retrieved

        program.asserting(_ shouldBe Some(OrderTicket))
      }
    }

    "should return all tickets" in {
      transactor.use { xa =>
        val program = for {
          tickets <- LiveTickets[IO](xa)
          retrieved <- tickets.all()
        } yield retrieved

        program.asserting(_ shouldBe List(OrderTicket))
      }
    }

    "should create a ticket" in {
      transactor.use { xa =>
        val program = for {
          tickets <- LiveTickets[IO](xa)
          ticketId <- tickets.create(NewTicketInfo)
          ticketOpt <- tickets.find(ticketId)
        } yield ticketOpt

        program.asserting(_.map(_.ticketInfo) shouldBe Some(NewTicketInfo))
      }
    }

    "should return a updated ticket only if that ticket exists" in {
      transactor.use { xa =>
        val program = for {
          tickets <- LiveTickets[IO](xa)
          ticketOpt <- tickets.update(TicketId, UpdatedTicket.ticketInfo)
        } yield ticketOpt

        program.asserting(_ shouldBe Some(UpdatedTicket))
      }
    }

    "should return None when updating a ticket that doesn't exist" in {
      transactor.use { xa =>
        val program = for {
          tickets <- LiveTickets[IO](xa)
          ticketOpt <- tickets.update(NotFoundTicketId, UpdatedTicket.ticketInfo)
        } yield ticketOpt

        program.asserting(_ shouldBe None)
      }
    }

    "should delete a ticket" in {
      transactor.use { xa =>
        val program = for {
          tickets <- LiveTickets[IO](xa)
          countOfDeletedTickets <- tickets.delete(TicketId)
          countOfTickets <- sql"SELECT COUNT(*) FROM tickets WHERE id = $TicketId".query[Int].unique.transact(xa)
        } yield (countOfDeletedTickets, countOfTickets)

        program.asserting {
          case (countOfDeletedTickets, countOfTickets) =>
            countOfDeletedTickets shouldBe 1
            countOfTickets shouldBe 0
        }
      }
    }

    "should return 0 for the number of updated rows when deleting a ticket that doesn't exist" in {
      transactor.use { xa =>
        val program = for {
          tickets <- LiveTickets[IO](xa)
          countOfDeletedTickets <- tickets.delete(NotFoundTicketId)
        } yield countOfDeletedTickets

        program.asserting(_ shouldBe 0)
      }
    }

    "should filter completed tickets" in {
      transactor.use { xa =>
        val program = for {
          tickets <- LiveTickets[IO](xa)
          filteredTickets <- tickets.all(TicketFilter(completed = true), Pagination.default)
        } yield filteredTickets

        program.asserting(_ shouldBe List.empty)
      }
    }

    "should filter tickets by orders" in {
      transactor.use { xa =>
        val program = for {
          tickets <- LiveTickets[IO](xa)
          filteredTickets <- tickets.all(TicketFilter(orders = List("late", "cappuccino", "cookie")), Pagination.default)
        } yield filteredTickets

        program.asserting(_ shouldBe List(OrderTicket))
      }
    }
  }
}
