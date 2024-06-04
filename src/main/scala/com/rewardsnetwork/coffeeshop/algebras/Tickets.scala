package com.rewardsnetwork.coffeeshop.algebras

import cats.*
import cats.implicits.*
import cats.effect.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.*
import org.typelevel.log4cats.Logger
import com.rewardsnetwork.coffeeshop.domain.ticket.*
import com.rewardsnetwork.coffeeshop.domain.pagination.*
import com.rewardsnetwork.coffeeshop.logging.syntax.*

trait Tickets[F[_]] {
  def create(ticketInfo: TicketInfo): F[java.util.UUID]
  def all(): F[List[Ticket]] // TODO: fix thoughts on the all method
  def all(filter: TicketFilter, pagination: Pagination): F[List[Ticket]]
  def find(id: java.util.UUID): F[Option[Ticket]]
  def update(id: java.util.UUID, ticketInfo: TicketInfo): F[Option[Ticket]]
  def delete(id: java.util.UUID): F[Int]
}

class LiveTickets[F[_]: MonadCancelThrow: Logger] private (xa: Transactor[F]) extends Tickets[F] {

  override def create(ticketInfo: TicketInfo): F[java.util.UUID] =
    sql"""
         INSERT INTO tickets(
          time,
          serverId,
          tableNumber,
          guestName,
          numberOfGuests,
          orders,
          completed
         ) VALUES (
          ${System.currentTimeMillis()},
          ${ticketInfo.serverId},
          ${ticketInfo.tableNumber},
          ${ticketInfo.guestName},
          ${ticketInfo.numberOfGuests},
          ${ticketInfo.orders},
          false
         )
       """.update
      .withUniqueGeneratedKeys[java.util.UUID]("id")
      .transact(xa)

  override def all(): F[List[Ticket]] =
    sql"""
     SELECT
        id,
        time,
        serverId,
        tableNumber,
        guestName,
        numberOfGuests,
        orders,
        completed
     FROM tickets
       """
      .query[Ticket]
      .to[List]
      .transact(xa)

  override def all(filter: TicketFilter, pagination: Pagination): F[List[Ticket]] = {
    val selectFragment: Fragment =
      fr"""
        SELECT
          id,
          time,
          serverId,
          tableNumber,
          guestName,
          numberOfGuests,
          orders,
          completed
        """

    val fromFragment: Fragment =
      fr"FROM tickets"

    val whereFragment: Fragment = Fragments.whereAndOpt(
      filter.beforeTime.map(beforeTime => fr"time > $beforeTime"),
      filter.afterTime.map(afterTime => fr"time < $afterTime"),
      filter.orders.toNel.map(orders =>
        Fragments.or(orders.toList.map(order => fr"$order=any(orders)"): _*)
      ),
      filter.completed.some.map(completed => fr"completed = $completed")
    )

    val paginationFragment: Fragment =
      fr"ORDER BY id LIMIT ${pagination.limit} OFFSET ${pagination.offset}"

    val statement = selectFragment |+| fromFragment |+| whereFragment |+| paginationFragment

    Logger[F].info(statement.toString) *>
      statement
        .query[Ticket]
        .to[List]
        .transact(xa)
        .logError(e => s"Failed query: ${e.getMessage}")
  }

  override def find(id: java.util.UUID): F[Option[Ticket]] =
    sql"""
      SELECT
        id,
        time,
        serverId,
        tableNumber,
        guestName,
        numberOfGuests,
        orders,
        completed
      FROM tickets
      WHERE id = $id
    """
      .query[Ticket]
      .option
      .transact(xa)

  override def update(id: java.util.UUID, ticketInfo: TicketInfo): F[Option[Ticket]] =
    sql"""
        UPDATE tickets
        SET
          tableNumber = ${ticketInfo.tableNumber},
          guestName = ${ticketInfo.guestName},
          numberOfGuests = ${ticketInfo.numberOfGuests},
          orders = ${ticketInfo.orders}
        WHERE id = $id
       """.update.run
      .transact(xa)
      .flatMap(_ => find(id)) // return the updated ticket

  override def delete(id: java.util.UUID): F[Int] =
    sql"""
         DELETE FROM tickets
         WHERE id = $id
       """.update.run
      .transact(xa)
}

object LiveTickets {

  given ticketRead: Read[Ticket] = Read[
    (
        java.util.UUID,       // id
        Long,                 // time
        java.util.UUID,       // serverId
        Int,                  // tableNumber
        String,               // guestName
        Int,                  // numberOfGuests
        Option[List[String]], // orders
        Boolean               // completed
    )
  ].map {
    case (
          id: java.util.UUID,
          time: Long,
          serverId: java.util.UUID,
          tableNumber: Int,
          guestName: String,
          numberOfGuests: Int,
          orders: Option[List[String]] @unchecked,
          completed: Boolean
        ) =>
      Ticket(
        id = id,
        time = time,
        ticketInfo = TicketInfo(
          serverId = serverId,
          tableNumber = tableNumber,
          guestName = guestName,
          numberOfGuests = numberOfGuests,
          orders = orders
        ),
        completed = completed
      )
  }

  def apply[F[_]: MonadCancelThrow: Logger](xa: Transactor[F]): F[LiveTickets[F]] =
    new LiveTickets[F](xa).pure[F]
}
