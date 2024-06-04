package com.rewardsnetwork.coffeeshop.http.validation

import io.circe.generic.auto.*
import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.implicits.*

import org.http4s.*
import org.http4s.dsl.*
import org.http4s.implicits.*
import org.http4s.circe.CirceEntityCodec.*
import org.typelevel.log4cats.Logger

import validators.*
import com.rewardsnetwork.coffeeshop.logging.syntax.*
import com.rewardsnetwork.coffeeshop.http.responses.FailResponse

object syntax {

  def validateEntity[A](entity: A)(using validator: Validator[A]): ValidationResult[A] =
    validator.validate(entity)

  trait HttpValidationDsl[F[_]: MonadThrow: Logger] extends Http4sDsl[F] {
    extension (req: Request[F])
      def validate[A: Validator](serverLogicIfValid: A => F[Response[F]])(using
          EntityDecoder[F, A]
      ): F[Response[F]] =
        req
          .as[A]
          .logError(e => s"Parsing failed: $e")
          .map(validateEntity)
          .flatMap {
            case Valid(entity) =>
              serverLogicIfValid(entity)
            case Invalid(errors) =>
              BadRequest(FailResponse(errors.toList.map(_.errorMessage).mkString(", ")))
          }
  }

}
