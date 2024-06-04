package com.rewardsnetwork.coffeeshop.domain

object pagination {
  final case class Pagination(limit: Int, offset: Int)

  object Pagination {
    val defaultPageSize = 20 // TODO: put this in config

    def apply(limitOpt: Option[Int], offsetOpt: Option[Int]) =
      new Pagination(limitOpt.getOrElse(defaultPageSize), offsetOpt.getOrElse(0))

    def default =
      Pagination(defaultPageSize, offset = 0)
  }
}
