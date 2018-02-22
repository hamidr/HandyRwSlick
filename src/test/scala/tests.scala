import org.scalatest._
import RwSlick.QueryHelper._
import RwSlick._
import Samples.Coffee
import cats.data.EitherT
import org.scalatest.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._

import scala.concurrent.{ExecutionContext, Future}

class ExampleSpec extends AsyncFlatSpec with MockitoSugar {
  implicit val db: ReadWriteDB = mock[ReadWriteDB]

  val readQueryHelper   = toQuery(Samples.readAction)
  val writeQueryHelper  = toQuery(Samples.writeAction)
  val rawQueryHelper    = toQuery(Samples.rawAction)
  val mixed1QueryHelper = toQuery(Samples.mixedAction1)
  val mixed2QueryHelper = toQuery(Samples.mixedAction2)

  "Different DBIOActions" should "match their own HandyQuery-s" in {
    assert(readQueryHelper.isInstanceOf[ReadQuery[Samples.Coffee]])
    assert(writeQueryHelper.isInstanceOf[WriteQuery[Int]])
    assert(rawQueryHelper.isInstanceOf[RawQuery[String]])
    assert(mixed1QueryHelper.isInstanceOf[MixedQuery[String]])
    assert(mixed2QueryHelper.isInstanceOf[MixedQuery[String]])
  }

  "Only read queries" should "be called on replica" in {
    val replica = mock[DatabaseHandler]
    val master  = mock[DatabaseHandler]

    when(replica.run[Any](any())) thenReturn Future.successful(Coffee(name = "replica", price = 0))
    when(master.run[Any](any())) thenReturn Future.successful(Coffee(name = "master", price = 0))

    val rwDB = new ReadWriteDB(master, replica)
    rwDB.run(Samples.readAction) map { r =>
      assert(r.name != "master")
      assert(r.name == "replica")
    }
  }

  "Write queries" should "be called on master" in {
    val replica = mock[DatabaseHandler]
    val master  = mock[DatabaseHandler]

    when(replica.run[Any](any())) thenReturn Future.successful(0)
    when(master.run[Any](any())) thenReturn Future.successful(1)

    val rwDB = new ReadWriteDB(master, replica)
    rwDB.run(Samples.writeAction) map { r =>
      assert(r != 0)
      assert(r == 1)
    }
  }

  "Raw queries" should "be called on master" in {
    val replica = mock[DatabaseHandler]
    val master  = mock[DatabaseHandler]

    when(replica.run[Any](any())) thenReturn Future.successful("replica")
    when(master.run[Any](any())) thenReturn Future.successful("master")

    val rwDB = new ReadWriteDB(master, replica)
    rwDB.run(Samples.rawAction) map { r =>
      assert(r != "replica")
      assert(r == "master")
    }
  }

  "fValueOr in HandyQueries" should "return Right if DB works" in {
    import cats.implicits._
    implicit val db: ReadWriteDB = mock[ReadWriteDB]
    when(db.runReplica(Samples.readAction)) thenReturn Future.successful(Coffee(name = "it works", price = 666))

    val handyQuery = {
      toQuery(Samples.readAction)
    }

    handyQuery.fValueOr.map(_.name).getOrElse("it doesn't work") map { v =>
      assert(v == "it works")
      assert(v != "it doesn't work")
    }
  }

  "fValueOr in HandyQueries for $query.head" should "be Left with NotFound if DB the returned sequence is empty" in {
    implicit val db: ReadWriteDB = mock[ReadWriteDB]
    when(db.runReplica(Samples.readAction)) thenReturn Future.failed(new java.util.NoSuchElementException)

    val handyQuery = {
      toQuery(Samples.readAction)
    }

    handyQuery.fValueOr.value.map {
      case Left(q @ NotFound(_)) =>
        assert(q.toString.contains("/src/test/scala/tests.scala"))

      case _ => assert(false)
    }
  }

  "fValueOr in HandyQueries for unknown exceptions" should "return Left with QueryError" in {
    implicit val db: ReadWriteDB = mock[ReadWriteDB]
    when(db.runReplica(Samples.readAction)) thenReturn Future.failed(new Exception("hello world!"))

    val handyQuery = {
      toQuery(Samples.readAction)
    }

    handyQuery.fValueOr.value.map {
      case Left(QueryError(_, _)) => assert(true)
      case _                      => assert(false)
    }
  }

  "fValueOr in HandyQueries" should "be usable with other Errors" in {
    import cats.implicits._

    trait MyBaseError
    case class DbError(error: RwSlick.BaseError) extends MyBaseError

    type AsyncResultT[R] = EitherT[Future, MyBaseError, R]
    val f: Future[Unit] = Future.successful(())

    import scala.concurrent.Future

    val eitherTee: AsyncResultT[Unit] = EitherT.liftF[Future, MyBaseError, Unit](f)

    implicit val db: ReadWriteDB = mock[ReadWriteDB]
    when(db.runReplica(Samples.readAction)) thenReturn Future.failed(new Exception("hello world!"))

    val handyQuery = {
      toQuery(Samples.readAction)
    }

    implicit class queryWrapper(val query: HandyQuery[_]) extends QueryWrapper {
      override type ReturnType = MyBaseError
      override def dbError(error: BaseError): MyBaseError = DbError(error)
    }

    val w = for {
      y <- eitherTee
      x <- handyQuery.runQuery //line 144 for tests
    } yield ()

    val z = for {
      x <- handyQuery.runQuery
      y <- eitherTee
    } yield ()

    (for {
      x <- w
      y <- z
    } yield ()).value map {
      case Left(DbError(e)) => assert(e.toString.contains("Line: 144"))
      case _                => assert(false)
    }
    assert(true)
  }
}
