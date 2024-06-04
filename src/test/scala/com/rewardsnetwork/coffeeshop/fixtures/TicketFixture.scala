package com.rewardsnetwork.coffeeshop.fixtures

import cats.syntax.all.*
import com.rewardsnetwork.coffeeshop.domain.ticket.*

import java.util.UUID

import com.rewardsnetwork.coffeeshop.domain.ticket

trait TicketFixture {

  val NotFoundTicketId = UUID.fromString("6ea79557-3112-4c84-a8f5-1d1e2c300948")

  val TicketId = UUID.fromString("243df418-ec6e-4d49-9279-f799c0f40064")

  val ServerId = UUID.fromString("443df418-cd4e-9f33-9279-f799c0f40064")

  val SecondServerId = UUID.fromString("033212f9-cd4e-9f33-9279-f799c0f40064")

  val OrderTicket = Ticket(
    TicketId,
    1659186086L,
    TicketInfo(
      ServerId,
      12,
      "Alexa",
      2,
      Some(List("late", "cappuccino", "cookie")),
    )
  )

  val InvalidTicket = Ticket(
    null,
    21L,
    TicketInfo.empty
  )

  val UpdatedTicket = Ticket(
    TicketId,
    1659186086L,
    TicketInfo(
      ServerId,
      12,
      "Alexa",
      2,
      Some(List("late", "cappuccino", "cookie", "sweet tea"))
    )
  )

  val NewTicketInfo = TicketInfo(
    SecondServerId,
    30,
    "Jordan",
    3,
    Some(List("frappe", "mocha", "root beer"))
  )

  val TicketWithNotFoundId = OrderTicket.copy(id = NotFoundTicketId)

  val SecondTicketId = UUID.fromString("19a941d0-aa19-477b-9ab0-a7033ae65c2b")
  val SecondTicket = OrderTicket.copy(id = SecondTicketId)
  
  
  val NewTicketId = UUID.fromString("efcd2a64-4463-453a-ada8-b11ae1db4377")
  val NewTicket = TicketInfo(
    ServerId,
    2,
    "James",
    4,
    Some(List("late", "ice coffee", "caramel late", "cold brew"))
  )
}

