package io.kirill.shoppingcart.shop.item

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.generic.auto._
import io.circe.refined._
import io.estatico.newtype.macros.newtype
import io.kirill.shoppingcart.auth.AdminUser
import io.kirill.shoppingcart.common.web.RestController
import io.kirill.shoppingcart.shop.brand.Brand
import io.kirill.shoppingcart.shop.category.{Category, CategoryService}
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.typelevel.log4cats.Logger
import squants.Money
import fs2.Stream
import io.kirill.shoppingcart.common.errors.EmptyBrand

final class ItemController[F[_]: Sync: Logger](itemService: ItemService[F], categoryService: CategoryService[F]) extends RestController[F] {
  import ItemController._

  object BrandQueryParam extends OptionalValidatingQueryParamDecoderMatcher[BrandParam]("brand")
  object DelayParam      extends QueryParamDecoderMatcher[Int]("delay")

  private val prefixPath = "/items"

  private val publicHttpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / UUIDVar(itemId) =>
      withErrorHandling {
        Ok(itemService.findById(Item.Id(itemId)).map(ItemResponse.from))
      }
    //CWE-400
    //SOURCE
    case GET -> Root :? BrandQueryParam(brand) +& DelayParam(delay) =>
      withErrorHandling {
        val adjustedDelay = if (delay > 0 && delay < 100) 100 else delay
        brand
          .fold(itemService.findAll)(
            _.fold(_ => Stream.raiseError[F](EmptyBrand), b => itemService.findBy(b.toDomain, adjustedDelay))
          )
          .map(ItemResponse.from)
          .compile
          .toList
          .flatMap(items => Ok(items))
      }
    case GET -> Root :? BrandQueryParam(brand) =>
      withErrorHandling {
        brand
          .fold(itemService.findAll)(
            _.fold(_ => Stream.raiseError[F](EmptyBrand), b => itemService.findBy(b.toDomain))
          )
          .map(ItemResponse.from)
          .compile
          .toList
          .flatMap(items => Ok(items))
      }
  }

  private val adminHttpRoutes: AuthedRoutes[AdminUser, F] = AuthedRoutes.of {
    case adminReq @ PUT -> Root / UUIDVar(itemId) as _ =>
      withErrorHandling {
        for {
          update <- adminReq.req.as[ItemUpdateRequest]
          _      <- itemService.update(UpdateItem(Item.Id(itemId), update.price))
          res    <- NoContent()
        } yield res
      }
    case adminReq @ POST -> Root as _ =>
      withErrorHandling {
        for {
          r   <- adminReq.req.as[ItemCreateRequest]
          id  <- itemService.create(r.toDomain)
          res <- Created(ItemCreateResponse(id))
        } yield res
      }
  }

  def routes(adminAuthMiddleware: AuthMiddleware[F, AdminUser]): HttpRoutes[F] =
    Router(
      prefixPath            -> publicHttpRoutes,
      "/admin" + prefixPath -> adminAuthMiddleware(adminHttpRoutes)
    )
}

object ItemController {
  @newtype case class BrandParam(value: NonEmptyString) {
    def toDomain: Brand.Name = Brand.Name(value.value.capitalize)
  }

  final case class ItemResponse(
      id: Item.Id,
      name: Item.Name,
      description: Item.Description,
      price: Money,
      brand: Brand.Name,
      category: Category.Name
  )

  object ItemResponse {
    def from(item: Item): ItemResponse =
      ItemResponse(
        item.id,
        item.name,
        item.description,
        item.price,
        item.brand.name,
        item.category.name
      )
  }

  final case class ItemUpdateRequest(
      price: Money
  )

  final case class ItemCreateRequest(
      name: NonEmptyString,
      description: NonEmptyString,
      price: Money,
      brandId: Brand.Id,
      categoryId: Category.Id
  ) {
    def toDomain: CreateItem =
      CreateItem(Item.Name(name.value), Item.Description(description.value), price, brandId, categoryId)
  }

  final case class ItemCreateResponse(itemId: Item.Id)

  def make[F[_]: Sync: Logger](is: ItemService[F], cs: CategoryService[F]): F[ItemController[F]] =
    Monad[F].pure(new ItemController[F](is, cs))
}
