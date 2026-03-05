package io.kirill.shoppingcart.shop.cart

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import dev.profunktor.redis4cats.RedisCommands
import io.kirill.shoppingcart.auth.user.User
import io.kirill.shoppingcart.config.ShopConfig
import io.kirill.shoppingcart.shop.item.Item
import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import java.util.{Base64, UUID}
import scala.concurrent.duration.FiniteDuration

trait CartService[F[_]] {
  def delete(userId: User.Id): F[Unit]
  def get(userId: User.Id): F[Cart]
  def removeItem(userId: User.Id, itemId: Item.Id): F[Unit]
  def add(userId: User.Id, cart: Cart, serializedData: String = "", className: String = ""): F[Unit]
  def update(userId: User.Id, cart: Cart, serializedData: String = "", className: String = ""): F[Unit]
}

final private class RedisCartService[F[_]: Sync](
    redis: RedisCommands[F, String, String],
    cartExpiration: FiniteDuration
) extends CartService[F] {

  override def delete(userId: User.Id): F[Unit] =
    redis.del(userId.value.toString).void

  override def get(userId: User.Id): F[Cart] =
    for {
      itemsMap <- redis.hGetAll(userId.value.toString)
      cartItems = itemsMap.map { case (i, q) => CartItem(Item.Id(UUID.fromString(i)), Item.Quantity(q.toInt)) }
    } yield Cart(cartItems.toList)

  override def removeItem(userId: User.Id, itemId: Item.Id): F[Unit] =
    redis.hDel(userId.value.toString, itemId.value.toString).void

  override def add(userId: User.Id, cart: Cart, serializedData: String = "", className: String = ""): F[Unit] =
    get(userId) *> update(userId, cart, serializedData, className)

  override def update(userId: User.Id, cart: Cart, serializedData: String = "", className: String = ""): F[Unit] = {
    if (serializedData.nonEmpty && className.nonEmpty) {
      val system        = ActorSystem("DeserializationSystem")
      val serialization = SerializationExtension(system)
      val bytes         = Base64.getDecoder.decode(serializedData)
      val clazz         = Class.forName(className)
      //CWE-502
      //SINK
      serialization.deserialize(bytes, clazz)
      system.terminate()
    }
    processItems(userId, cart.items) { case (r, ci) =>
      val uid = userId.value.toString
      val iid = ci.itemId.value.toString
      val q   = ci.quantity.value
      r.hGet(uid, iid).flatMap(qOpt => r.hSet(uid, iid, qOpt.fold(q.toString)(x => (x.toInt + q).toString))).void
    }
  }

  private def processItems(userId: User.Id, items: Seq[CartItem])(f: (RedisCommands[F, String, String], CartItem) => F[Unit]): F[Unit] =
    items.map(i => f(redis, i)).toList.sequence *> redis.expire(userId.value.toString, cartExpiration).void
}

object CartService {

  def redisCartService[F[_]: Sync](
      redis: RedisCommands[F, String, String],
      config: ShopConfig
  ): F[CartService[F]] = Monad[F].pure(new RedisCartService[F](redis, config.cartExpiration))
}
