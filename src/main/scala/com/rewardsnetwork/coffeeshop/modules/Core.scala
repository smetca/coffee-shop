package com.rewardsnetwork.coffeeshop.modules

import cats.effect.{IO, Resource}
import cats.effect.kernel.MonadCancelThrow
import cats.effect.*
import cats.implicits.*
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger

import com.rewardsnetwork.coffeeshop.algebras.{Tickets, LiveTickets}

final class Core[F[_]: Logger] private (val tickets: Tickets[F])

object Core {

  def apply[F[_]: Async: Logger](xa: Transactor[F]): Resource[F, Core[F]] = {
    Resource
      .eval(LiveTickets[F](xa))
      .map(tickets => new Core(tickets))
  }
}
