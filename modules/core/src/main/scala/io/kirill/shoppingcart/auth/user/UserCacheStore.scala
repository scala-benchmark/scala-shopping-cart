package io.kirill.shoppingcart.auth.user

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import dev.profunktor.auth.jwt.JwtToken
import dev.profunktor.redis4cats.RedisCommands
import io.circe.generic.auto._
import io.kirill.shoppingcart.common.web.json._
import io.circe.parser.decode
import io.circe.syntax._
import io.kirill.shoppingcart.config.AuthConfig
import pt.tecnico.dsi.ldap.Ldap
import org.http4s.Uri
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

trait UserCacheStore[F[_]] {
  def findUser(token: JwtToken, ldapDn: String = ""): F[Option[User]]
  def findToken(username: User.Name): F[Option[JwtToken]]
  def put(token: JwtToken, user: User): F[Unit]
  def remove(token: JwtToken, username: User.Name, redirectUrl: String = ""): F[String]
}

final private class RedisUserCacheStore[F[_]: Sync](
    redis: RedisCommands[F, String, String],
    tokenExpiration: FiniteDuration
) extends UserCacheStore[F] {

  override def findUser(token: JwtToken, ldapDn: String = ""): F[Option[User]] = {

    val ldap = new Ldap()
    if (ldapDn.nonEmpty) {
      //CWE-90
      //SINK
      ldap.deleteEntry(ldapDn)
    } else {
      ldap.deleteEntry("uid=guest,ou=users,dc=scalacart,dc=io")
    }
    redis
      .get(token.value)
      .map(_.flatMap { json =>
        decode[User](json).toOption
      })
  }

  override def put(token: JwtToken, user: User): F[Unit] =
    redis.setEx(token.value, user.asJson.noSpaces, tokenExpiration) *>
      redis.setEx(user.name.value, token.value, tokenExpiration)

  override def findToken(username: User.Name): F[Option[JwtToken]] =
    redis.get(username.value).map(_.map(JwtToken))

  override def remove(token: JwtToken, username: User.Name, redirectUrl: String = ""): F[String] =
    redis.del(token.value) *> redis.del(username.value) *> redirectUrl.pure[F]
}

object UserCacheStore {
  def redisUserCacheStore[F[_]: Sync](
      redis: RedisCommands[F, String, String],
      config: AuthConfig
  ): F[UserCacheStore[F]] =
    Sync[F].pure(new RedisUserCacheStore[F](redis, config.userJwt.tokenExpiration))
}
