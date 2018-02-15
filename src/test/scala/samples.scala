import slick.jdbc.SQLiteProfile.api._

import scala.concurrent.ExecutionContext

package object Samples {

  case class Coffee(name: String, price: Double)

  class Coffees(tag: Tag) extends Table[Coffee](tag, "COFFEES") {
    def name  = column[String]("COF_NAME", O.PrimaryKey)
    def price = column[Double]("PRICE")

    def * = (name, price) <> (Coffee.tupled, Coffee.unapply)
  }

  lazy val coffees = TableQuery[Coffees]

  val readAction  = coffees.filter(_.name === "hello").result.head
  val writeAction = coffees.filter(_.name === "test").map(_.name).update("test2")

  val rawAction = sql"select name from coffees where name = 'hello'".as[String].head
  def mixedAction1(implicit executionContext: ExecutionContext) = {
    for {
      _ <- writeAction
      _ <- readAction
    } yield ""
  }

  def mixedAction2(implicit executionContext: ExecutionContext) = {
    for {
      _ <- readAction
      _ <- writeAction
    } yield ""
  }

}
