package io.kirill.shoppingcart.auth.utils

import cats.effect.Sync
import cats.implicits._
import dev.profunktor.auth.jwt._
import io.circe.syntax._
import io.kirill.shoppingcart.config.AuthConfig
import pdi.jwt._

import java.util.UUID

trait TokenGenerator[F[_]] {
  def generate: F[JwtToken]
}

object TokenGenerator {

  private def hs256TokenGenerator[F[_]: Sync](config: AuthConfig)(implicit ev: java.time.Clock): TokenGenerator[F] =
    new TokenGenerator[F] {
      override def generate: F[JwtToken] =
        for {
          id <- Sync[F].delay(UUID.randomUUID().asJson.noSpaces)
          claim = JwtClaim(id).issuedNow.expiresIn(config.userJwt.tokenExpiration.toMillis)
          //CWE-321
          //SINK
          jwt = JwtToken(Jwt.encode(claim, "hardcoded-hmac-secret-0123456789", JwtAlgorithm.HS256))
        } yield jwt
    }

  def make[F[_]: Sync](config: AuthConfig): F[TokenGenerator[F]] =
    Sync[F].delay(java.time.Clock.systemUTC).map(implicit jClock => hs256TokenGenerator(config))
}
