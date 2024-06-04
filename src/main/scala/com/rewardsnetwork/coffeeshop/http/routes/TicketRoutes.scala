package com.rewardsnetwork.coffeeshop.http.routes

import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.*
import cats.*
import cats.effect.*
import cats.implicits.*
import com.rewardsnetwork.coffeeshop.algebras.*
import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*
import org.typelevel.log4cats.Logger

import java.util.UUID
import scala.collection.mutable

import com.rewardsnetwork.coffeeshop.domain.ticket.*
import com.rewardsnetwork.coffeeshop.domain.pagination.*
import com.rewardsnetwork.coffeeshop.domain.pagination.Pagination
import com.rewardsnetwork.coffeeshop.http.responses.FailResponse
import com.rewardsnetwork.coffeeshop.logging.syntax.*
import com.rewardsnetwork.coffeeshop.http.validation.syntax.*

class TicketRoutes[F[_]: Concurrent: Logger] private (tickets: Tickets[F])
    extends HttpValidationDsl[F] {

  object OffsetQParam extends OptionalQueryParamDecoderMatcher[Int]("offset")
  object LimitQParam  extends OptionalQueryParamDecoderMatcher[Int]("limit")

  // POST /tickets?limit=a&offset=b {filters}
  private val allTicketsRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root :? LimitQParam(limit) +& OffsetQParam(offset) =>
      for {
        filter     <- req.as[TicketFilter]
        ticketList <- tickets.all(filter, Pagination(limit, offset))
        response   <- Ok(ticketList)
      } yield response
  }

  // GET /tickets/uuid
  private val findTicketRoute: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root / UUIDVar(id) =>
    tickets.find(id).flatMap {
      case Some(ticket) => Ok(ticket)
      case None =>
        NotFound(FailResponse(s"Ticket $id not found."))
    }
  }

  // POST /tickets/create {ticketInfo}
  private val createTicketRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "create" =>
      req.validate[TicketInfo] { ticketInfo =>
        for {
          ticketId <- tickets.create(ticketInfo)
          resp     <- Created(ticketId)
        } yield resp
      }
  }

  // PUT /tickets/uuid {ticketInfo}
  private val updateTicketRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ PUT -> Root / UUIDVar(id) =>
      req.validate[TicketInfo] { ticketInfo =>
        for {
          ticketOpt <- tickets.update(id, ticketInfo)
          response <- ticketOpt match {
            case Some(ticket) => Ok()
            case None         => NotFound(FailResponse(s"Cannot update ticket $id: not found"))
          }
        } yield response
      }
  }

  // DELETE /tickets/uuid
  private val deleteTicketRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ DELETE -> Root / UUIDVar(id) =>
      tickets.find(id).flatMap {
        case Some(ticket) =>
          for {
            _        <- tickets.delete(id)
            response <- Ok()
          } yield response
        case None => NotFound(FailResponse(s"Cannot delete ticket $id: not found"))
      }
  }

  val routes = Router(
    "/tickets" ->
      (allTicketsRoute <+> findTicketRoute <+> createTicketRoute <+> updateTicketRoute <+> deleteTicketRoute)
  )

}

object TicketRoutes {
  def apply[F[_]: Concurrent: Logger](tickets: Tickets[F]) = new TicketRoutes[F](tickets)
}
