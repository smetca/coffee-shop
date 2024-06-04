package com.rewardsnetwork.coffeeshop.algebras

import cats.effect.*
import cats.implicits.*

import org.typelevel.log4cats.Logger
import tsec.authentication.{AugmentedJWT, JWTAuthenticator}
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt

import com.rewardsnetwork.coffeeshop.domain.security.*
import com.rewardsnetwork.coffeeshop.domain.auth.*
import com.rewardsnetwork.coffeeshop.domain.user.*

trait Auth[F[_]] {
  def login(email: String, password: String): F[Option[AuthToken]]
  def signUp(newUserInfo: NewUserInfo): F[Option[User]]
  def changePassword(
      email: String,
      newPasswordInfo: NewPasswordInfo
  ): F[Either[String, Option[User]]]

  def authenticator: Authenticator[F]
}

class LiveAuth[F[_]: Async: Logger] private (
    users: Users[F],
    override val authenticator: Authenticator[F]
) extends Auth[F] {
  override def login(email: String, password: String): F[Option[AuthToken]] =
    for {
      userOpt <- users.find(email)
      validatedUserOpt <- userOpt.filterA(user =>
        BCrypt.checkpwBool[F](password, PasswordHash[BCrypt](user.hashedPassword))
      )
      tokenOpt <- validatedUserOpt.traverse(user => authenticator.create(user.email))
    } yield tokenOpt
  
  override def signUp(newUserInfo: NewUserInfo): F[Option[User]] =
    users.find(newUserInfo.email).flatMap {
      case Some(_) => None.pure[F]
      case None =>
        for {
          hashedPassword <- BCrypt.hashpw[F](newUserInfo.password)
          user <- User(
            newUserInfo.email,
            hashedPassword,
            newUserInfo.firstName,
            newUserInfo.lastName,
            Role.SERVER
          ).pure[F]
          _ <- users.create(user)
        } yield Some(user)
    }

  override def changePassword(
      email: String,
      newPasswordInfo: NewPasswordInfo
  ): F[Either[String, Option[User]]] = {
    def updateUser(user: User, newPassword: String): F[Option[User]] =
      for {
        hashedPassword <- BCrypt.hashpw[F](newPasswordInfo.newPassword)
        updatedUser    <- users.update(user.copy(hashedPassword = hashedPassword))
      } yield updatedUser

    def checkAndUpdate(
        user: User,
        oldPassword: String,
        newPassword: String
    ): F[Either[String, Option[User]]] =
      for {
        passCheck <- BCrypt
          .checkpwBool[F](newPasswordInfo.oldPassword, PasswordHash[BCrypt](user.hashedPassword))
        updateResult <-
          if (passCheck) updateUser(user, newPassword).map(Right(_))
          else Left("Invalid Password").pure[F]
      } yield updateResult

    users.find(email).flatMap {
      case None => Right(None).pure[F]
      case Some(user) =>
        val NewPasswordInfo(oldPassword, newPassword) = newPasswordInfo
        checkAndUpdate(user, oldPassword, newPassword)
    }
  }
}

object LiveAuth {
  def apply[F[_]: Async: Logger](
      users: Users[F],
      authenticator: Authenticator[F]
  ) = new LiveAuth[F](users, authenticator).pure[F]
}
