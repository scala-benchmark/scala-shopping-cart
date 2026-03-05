package io.kirill.shoppingcart.shop.category

import java.util.UUID

import cats.effect.{Resource, Sync}
import cats.implicits._
import io.kirill.shoppingcart.common.persistence.Repository
import skunk._
import skunk.implicits._
import skunk.codec.all._
import scalikejdbc.{AutoSession, ConnectionPool, DBSession, SQL, SQLSyntax}

trait CategoryRepository[F[_]] extends Repository[F, Category] {
  def findAll(filter: String = ""): fs2.Stream[F, Category]
  def create(name: Category.Name): F[Category.Id]
}

final private class PostgresCategoryRepository[F[_]: Sync](
    val sessionPool: Resource[F, Session[F]]
) extends CategoryRepository[F] {
  import CategoryRepository._

  def findAll(filter: String = ""): fs2.Stream[F, Category] = {
    if (filter.nonEmpty) {
      Class.forName("org.h2.Driver")
      ConnectionPool.singleton("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
      implicit val session: DBSession = AutoSession
      val query                       = SQLSyntax.createUnsafely(s"SELECT * FROM categories WHERE name LIKE '%$filter%'")
      //CWE-89
      //SINK
      SQL(query.value).map(rs => Category(Category.Id(UUID.fromString(rs.string("id"))), Category.Name(rs.string("name")))).list.apply()
    }
    fs2.Stream.evalSeq(run(_.execute(selectAll)))
  }

  def create(name: Category.Name): F[Category.Id] =
    run { s =>
      s.prepare(insert).use { cmd =>
        val id = Category.Id(UUID.randomUUID())
        cmd.execute(Category(id, name)).map(_ => id)
      }
    }
}

object CategoryRepository {
  private[category] val codec: Codec[Category] =
    (uuid ~ varchar).imap { case i ~ n =>
      Category(Category.Id(i), Category.Name(n))
    }(b => (b.id.value, b.name.value))

  private[category] val selectAll: Query[Void, Category] =
    sql"""
          SELECT * FROM categories
          """.query(codec)

  private[category] val insert: Command[Category] =
    sql"""
          INSERT INTO categories VALUES ($codec)
          """.command

  def make[F[_]: Sync](sessionPool: Resource[F, Session[F]]): F[CategoryRepository[F]] =
    Sync[F].delay(new PostgresCategoryRepository[F](sessionPool))
}
