package com.rewardsnetwork.coffeeshop.domain

import java.util.UUID

object ticket {
  case class Ticket(
      id: UUID,
      time: Long,
      ticketInfo: TicketInfo,
      completed: Boolean = false
  )

  case class TicketInfo(
      serverId: UUID,
      tableNumber: Int,
      guestName: String,
      numberOfGuests: Int,
      orders: Option[List[String]] // Simple representation only for demo purposes
  )

  object TicketInfo {
    val empty: TicketInfo =
      TicketInfo(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"), 0, "", 0, None)

    def minimal(
        serverId: UUID,
        tableNumber: Int,
        guestName: String,
        numberOfGuests: Int
    ) = TicketInfo(
      serverId = serverId,
      tableNumber = tableNumber,
      guestName = guestName,
      numberOfGuests = numberOfGuests,
      orders = None
    )
  }

  final case class TicketFilter(
      beforeTime: Option[Long] = None,
      afterTime: Option[Long] = None,
      orders: List[String] = List.empty,
      completed: Boolean = false
  )
}
