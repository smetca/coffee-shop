package com.rewardsnetwork.coffeeshop.modules

import cats.*
import cats.effect.*
import cats.implicits.*

import org.http4s.*
import org.http4s.dsl.*
import org.http4s.dsl.impl.*
import org.http4s.server.*
import org.typelevel.log4cats.Logger

import com.rewardsnetwork.coffeeshop.http.routes.{HealthRoutes, TicketRoutes}

class HttpApi[F[_]: Concurrent: Logger] private (core: Core[F]) {
  private val healthRoutes = HealthRoutes[F].routes
  private val ticketRoutes = TicketRoutes[F](core.tickets).routes

  val endpoints = Router(
    "/api" -> (healthRoutes <+> ticketRoutes)
  )
}

object HttpApi {
  def apply[F[_]: Concurrent: Logger](core: Core[F]): Resource[F, HttpApi[F]] =
    Resource.pure(new HttpApi[F](core))
}
