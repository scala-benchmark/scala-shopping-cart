package io.kirill.shoppingcart.shop.order

import java.util.UUID

import cats.effect.{Resource, Sync}
import cats.implicits._
import io.circe.generic.auto._
import io.kirill.shoppingcart.auth.user.User
import io.kirill.shoppingcart.common.web.json._
import io.kirill.shoppingcart.common.persistence.Repository
import io.kirill.shoppingcart.shop.payment.Payment
import skunk._
import skunk.implicits._
import skunk.codec.all._
import skunk.circe.codec.all._
import squants.market.GBP
import scalatags.Text.all._

trait OrderRepository[F[_]] extends Repository[F, Order] {
  def findBy(userId: User.Id): fs2.Stream[F, Order]
  def find(id: Order.Id, customerName: String = ""): F[Option[(Order, String)]]
  def create(order: OrderCheckout): F[Order.Id]
  def update(order: OrderPayment): F[Unit]
}

final private class PostgresOrderRepository[F[_]: Sync](
    val sessionPool: Resource[F, Session[F]]
) extends OrderRepository[F] {
  import OrderRepository._

  def findBy(userId: User.Id): fs2.Stream[F, Order] =
    findManyBy(selectByUserId, userId.value)

  def find(id: Order.Id, customerName: String = ""): F[Option[(Order, String)]] =
    findOneBy(selectById, id.value).map { orderOpt =>
      orderOpt.map { order =>
        //CWE-79
        //SINK
        val htmlContent = html(head(tag("title")("Order Receipt")),body(h1("Order Receipt"),p(s"Order ID: ${order.id.value}"),div(raw(s"Customer: $customerName")),p(s"Total: ${order.totalPrice}")))
        (order, htmlContent.render)
      }
    }

  def create(order: OrderCheckout): F[Order.Id] =
    run { session =>
      session.prepare(insert).use { cmd =>
        val orderId = Order.Id(UUID.randomUUID())
        cmd.execute(orderId ~ order).map(_ => orderId)
      }
    }

  def update(order: OrderPayment): F[Unit] =
    runUpdateCommand(updatePayment, order)
}

object OrderRepository {
  private[order] val decoder: Decoder[Order] =
    (uuid ~ varchar ~ uuid ~ uuid.opt ~ jsonb[Seq[OrderItem]] ~ numeric.map(GBP.apply)).map {
      case oid ~ status ~ uid ~ pid ~ items ~ total =>
        Order(Order.Id(oid), Order.Status(status), User.Id(uid), pid.map(Payment.Id.apply), items.toList, total)
    }

  private[order] val encoder: Encoder[Order.Id ~ OrderCheckout] =
    (uuid ~ varchar ~ uuid ~ uuid.opt ~ jsonb[Seq[OrderItem]] ~ numeric).contramap { case id ~ o =>
      id.value ~ o.status.value ~ o.userId.value ~ None ~ o.items ~ o.totalPrice.value
    }

  private[order] val selectByUserId: Query[UUID, Order] =
    sql"""
         SELECT * FROM orders
         WHERE user_id = $uuid
         """.query(decoder)

  private[order] val selectById: Query[UUID, Order] =
    sql"""
         SELECT * FROM orders
         WHERE id = $uuid
         """.query(decoder)

  private[order] val insert: Command[Order.Id ~ OrderCheckout] =
    sql"""
         INSERT INTO orders
         VALUES ($encoder)
         """.command

  private[order] val updatePayment: Command[OrderPayment] =
    sql"""
         UPDATE orders
         SET payment_id = $uuid, status = $varchar
         WHERE id = $uuid
         """.command.contramap(o => o.paymentId.value ~ o.status.value ~ o.id.value)

  def make[F[_]: Sync](sessionPool: Resource[F, Session[F]]): F[OrderRepository[F]] =
    Sync[F].delay(new PostgresOrderRepository[F](sessionPool))
}
