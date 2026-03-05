package io.kirill.shoppingcart.shop.order

import cats.Monad

import cats.effect.Sync
import cats.implicits._
import org.typelevel.log4cats.Logger
import io.circe.generic.auto._
import io.circe.refined._
import io.kirill.shoppingcart.auth.CommonUser
import io.kirill.shoppingcart.common.errors.EmptyCart
import io.kirill.shoppingcart.common.web.json._
import io.kirill.shoppingcart.common.web.RestController
import io.kirill.shoppingcart.shop.cart.CartService
import io.kirill.shoppingcart.shop.item.ItemService
import io.kirill.shoppingcart.shop.payment.{Card, Payment, PaymentService}
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.{AuthedRoutes, HttpRoutes, MediaType}
import org.http4s.headers.`Content-Type`
import org.http4s.circe._
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import squants.market.{GBP, Money}

final class OrderController[F[_]: Sync: Logger](
    orderService: OrderService[F],
    cartService: CartService[F],
    itemService: ItemService[F],
    paymentService: PaymentService[F]
) extends RestController[F] {
  import OrderController._

  private val prefixPath = "/orders"

  private val httpRoutes: AuthedRoutes[CommonUser, F] =
    AuthedRoutes.of {
      case GET -> Root as user =>
        withErrorHandling {
          Ok(orderService.findBy(user.value.id).map(OrderResponse.from).compile.toList)
        }
      //CWE-79
      //SOURCE
      case GET -> Root / UUIDVar(orderId) :? CustomerNameParam(customerName) as user =>
        withErrorHandling {
          orderService.get(user.value.id, Order.Id(orderId), customerName).flatMap { case (_, html) =>
            Ok(html).map(_.withContentType(`Content-Type`(MediaType.text.html)))
          }
        }
      case GET -> Root / UUIDVar(orderId) as user =>
        withErrorHandling {
          orderService.get(user.value.id, Order.Id(orderId)).flatMap { case (order, _) =>
            Ok(OrderResponse.from(order))
          }
        }
      case POST -> Root / "checkout" as user =>
        withErrorHandling {
          for {
            cart  <- cartService.get(user.value.id).ensure(EmptyCart)(_.items.nonEmpty)
            items <- cart.items.traverse(ci => itemService.findById(ci.itemId).map((_, ci.quantity)))
            orderItems = items.map { case (i, q) => OrderItem(i.id, i.price, q) }
            total      = items.foldLeft(GBP(0)) { case (total, (i, q)) => total + (i.price * q.value) }
            orderId <- orderService.create(OrderCheckout(user.value.id, orderItems, total))
            _       <- cartService.delete(user.value.id)
            res     <- Created(OrderCheckoutResponse(orderId))
          } yield res
        }
      case authedReq @ POST -> Root / UUIDVar(orderId) / "payment" as user =>
        withErrorHandling {
          for {
            paymentReq    <- authedReq.req.as[OrderPaymentRequest]
            orderTuple    <- orderService.get(user.value.id, Order.Id(orderId))
            order          = orderTuple._1
            pid           <- paymentService.process(Payment(order, paymentReq.card))
            _             <- orderService.update(OrderPayment(order.id, pid))
            res           <- NoContent()
          } yield res
        }
    }

  def routes(authMiddleware: AuthMiddleware[F, CommonUser]): HttpRoutes[F] =
    Router(prefixPath -> authMiddleware(httpRoutes))
}

object OrderController {
  object CustomerNameParam extends QueryParamDecoderMatcher[String]("name")

  final case class OrderPaymentRequest(
      card: Card
  )

  final case class OrderCheckoutResponse(orderId: Order.Id)

  final case class OrderResponse(
      id: Order.Id,
      status: Order.Status,
      items: List[OrderItem],
      totalPrice: Money
  )

  object OrderResponse {
    def from(order: Order): OrderResponse =
      OrderResponse(
        order.id,
        order.status,
        order.items,
        order.totalPrice
      )
  }

  def make[F[_]: Sync: Logger](
      os: OrderService[F],
      cs: CartService[F],
      is: ItemService[F],
      ps: PaymentService[F]
  ): F[OrderController[F]] =
    Monad[F].pure(new OrderController[F](os, cs, is, ps))
}
