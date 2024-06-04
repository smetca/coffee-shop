package com.rewardsnetwork.coffeeshop.domain

import com.rewardsnetwork.coffeeshop.domain.user.User
import tsec.authentication.{AugmentedJWT, JWTAuthenticator, SecuredRequest}
import tsec.mac.jca.HMACSHA256
import com.rewardsnetwork.coffeeshop.domain.user.*
import org.http4s.Response

object security {
  type Crypt               = HMACSHA256
  type AuthToken           = AugmentedJWT[Crypt, String]
  type Authenticator[F[_]] = JWTAuthenticator[F, String, User, Crypt]
  type AuthRoute[F[_]]     = PartialFunction[SecuredRequest[F, User, AuthToken], F[Response[F]]]
}
