package io.kirill.shoppingcart.shop.order

import cats.effect.Sync
import cats.implicits._
import io.kirill.shoppingcart.auth.user.User
import io.kirill.shoppingcart.common.errors.{OrderDoesNotBelongToThisUser, OrderNotFound}

trait OrderService[F[_]] {
  def get(userId: User.Id, orderId: Order.Id, customerName: String = ""): F[(Order, String)]
  def findBy(userId: User.Id): fs2.Stream[F, Order]
  def create(order: OrderCheckout): F[Order.Id]
  def update(order: OrderPayment): F[Unit]
}

final private class LiveOrderService[F[_]: Sync](
    orderRepository: OrderRepository[F]
) extends OrderService[F] {

  override def get(userId: User.Id, orderId: Order.Id, customerName: String = ""): F[(Order, String)] =
    orderRepository.findBy(userId).compile.drain *>
      orderRepository.find(orderId, customerName).flatMap {
        case None                                  => OrderNotFound(orderId).raiseError[F, (Order, String)]
        case Some((o, _)) if o.userId != userId    => OrderDoesNotBelongToThisUser(o.id, userId).raiseError[F, (Order, String)]
        case Some((o, html))                       => (o, html).pure[F]
      }

  override def findBy(userId: User.Id): fs2.Stream[F, Order] =
    orderRepository.findBy(userId)

  override def create(order: OrderCheckout): F[Order.Id] =
    orderRepository.create(order)

  override def update(order: OrderPayment): F[Unit] =
    orderRepository.update(order)
}

object OrderService {

  def make[F[_]: Sync](orderRepository: OrderRepository[F]): F[OrderService[F]] =
    Sync[F].delay(new LiveOrderService[F](orderRepository))
}
