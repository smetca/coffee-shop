package com.rewardsnetwork.coffeeshop.http.validation

import cats.*
import cats.data.*
import cats.implicits.*
import cats.data.Validated.*

import java.net.{URI, URL}
import scala.util.{Failure, Success, Try}
import com.rewardsnetwork.coffeeshop.domain.auth.*
import com.rewardsnetwork.coffeeshop.domain.ticket.*
import com.rewardsnetwork.coffeeshop.domain.user.*

object validators {

  sealed trait ValidationFailure(val errorMessage: String)
  case class EmptyField(fieldName: String) extends ValidationFailure(s"'$fieldName' is empty")
  case class InvalidEmail(fieldName: String)
      extends ValidationFailure(s"'$fieldName' is not a valid email")

  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]

  trait Validator[A] {
    def validate(value: A): ValidationResult[A]
  }

  val emptyUuid = "00000000-0000-0000-0000-000000000000"

  val emailCheckRegex =
    """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  def validateEmail(field: String, fieldName: String): ValidationResult[String] =
    if (emailCheckRegex.findFirstMatchIn(field).isDefined) field.validNel
    else InvalidEmail(fieldName).invalidNel

  def validateRequired[A](field: A, fieldName: String)(
      required: A => Boolean
  ): ValidationResult[A] =
    if (required(field)) field.validNel
    else EmptyField(fieldName).invalidNel

  given ticketInfoValidator: Validator[TicketInfo] = (ticketInfo: TicketInfo) => {
    val TicketInfo(
      serverId,       // should not be empty
      tableNumber,    // should not be empty
      guestName,      // should not be empty
      numberOfGuests, // should not be empty
      orders
    ) = ticketInfo

    val validServerId       = validateRequired(serverId, "serverId")(_.toString != emptyUuid)
    val validTableNumber    = validateRequired(tableNumber, "tableNumber")(_ > 0)
    val validGuestName      = validateRequired(guestName, "guestName")(_.nonEmpty)
    val validNumberOfGuests = validateRequired(numberOfGuests, "numberOfGuests")(_ > 0)

    (
      validServerId,         // serverId
      validTableNumber,      // tableNumber
      validGuestName,        // guestName
      validNumberOfGuests,   // numberOfGuests
      orders.validNel        // orders
    ).mapN(TicketInfo.apply) // ValidatedNel[ValidationFailure, TicketInfo]
  }

  given loginInfoValidator: Validator[LoginInfo] = (loginInfo: LoginInfo) => {
    val validUserEmail = validateRequired(loginInfo.email, "email")(_.nonEmpty)
      .andThen(e => validateEmail(e, "email"))

    val validUserPassword = validateRequired(loginInfo.password, "password")(_.nonEmpty)
    (validUserEmail, validUserPassword).mapN(LoginInfo.apply)
  }

  given newUserInfoValidator: Validator[NewUserInfo] = (newUserInfo: NewUserInfo) => {
    val validUserEmail = validateRequired(newUserInfo.email, "email")(_.nonEmpty)
      .andThen(e => validateEmail(e, "email"))

    val validUserPassword =
      validateRequired(newUserInfo.password, "password")(pass => pass.nonEmpty && pass.length > 8)

    (
      validUserEmail,
      validUserPassword,
      newUserInfo.firstName.validNel,
      newUserInfo.lastName.validNel,
    ).mapN(NewUserInfo.apply)
  }

  given newPasswordInfoValidator: Validator[NewPasswordInfo] = (newPasswordInfo: NewPasswordInfo) =>
    {
      val validOldPassword = validateRequired(newPasswordInfo.oldPassword, "oldPassword")(pass =>
        pass.nonEmpty && pass.length > 8
      )

      val validNewPassword = validateRequired(newPasswordInfo.newPassword, "newPassword")(pass =>
        pass.nonEmpty && pass.length > 8
      )

      (
        validOldPassword,
        validNewPassword
      ).mapN(NewPasswordInfo.apply)
    }
}
