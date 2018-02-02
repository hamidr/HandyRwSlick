import Utils.RwDummies._
import cats.data.EitherT
import cats.implicits._
import slick.dbio.{DBIOAction, Effect, NoStream}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal

package object RwSlick {

  trait DatabaseError

  case class NotFound(typeName: String)                 extends DatabaseError
  case class QueryError(e: Throwable, dumpInfo: String) extends DatabaseError

  type BaseQueryT[R] = DBIOAction[R, NoStream, _]

  trait DataBaseIO {
    type R
    type ActionType <: BaseQueryT[R]
  }

  trait MyDataBaseIO[T, Eff <: Effect] extends DataBaseIO {
    final type R          = T
    final type ActionType = DBIOAction[R, NoStream, Eff]
  }

  type ReadQueryT[R]  = MyDataBaseIO[R, Effect.Read]
  type WriteQueryT[R] = MyDataBaseIO[R, Effect.Write]
  type RawQueryT[R]   = MyDataBaseIO[R, Effect]

  trait DatabaseHandler {
    def run[R](query: BaseQueryT[R]): Future[R]
  }

  class ReadWriteDB(
      writerDb: DatabaseHandler,
      readerDB: DatabaseHandler
  )(implicit executionContext: ExecutionContext) {

    def runReplica[R](query: ReadQueryT[R]#ActionType) = readerDB.run(query)

    def runMaster[R](query: WriteQueryT[R]#ActionType)                       = writerDb.run(query)
    def runMaster[R](query: RawQueryT[R]#ActionType)(implicit dummy: Dummy1) = writerDb.run(query)

    def run[R](query: ReadQueryT[R]#ActionType)(implicit dummy: Dummy1)  = runReplica(query)
    def run[R](query: WriteQueryT[R]#ActionType)(implicit dummy: Dummy2) = runMaster(query)
    def run[R](query: RawQueryT[R]#ActionType)(implicit dummy: Dummy3)   = runMaster(query)

    def runRawReplica[R](query: RawQueryT[R]#ActionType) = readerDB.run(query)
  }

  object QueryHelper {
    import Utils.RwDummies.{Dummy1, Dummy2, Dummy3, Dummy4}

    def toQuery[R](query: WriteQueryT[R]#ActionType)(implicit ec: ExecutionContext, db: ReadWriteDB, dummy: Dummy1) =
      WriteQuery(query)

    def toQuery[R](query: ReadQueryT[R]#ActionType)(implicit ec: ExecutionContext, db: ReadWriteDB, dummy: Dummy2) =
      ReadQuery(query)

    def toQuery[R](query: RawQueryT[R]#ActionType)(implicit ec: ExecutionContext, db: ReadWriteDB, dummy: Dummy3) =
      RawQuery(query)
  }

  trait HandyQuery[QueryT <: DataBaseIO] {

    final type R           = QueryT#R
    final type Result      = Future[Either[DatabaseError, R]]
    final type ResultT     = EitherT[Future, DatabaseError, R]
    final type FetchResult = Future[R]

    protected def query: QueryT#ActionType

    def db: ReadWriteDB
    implicit def ec: ExecutionContext

    lazy val fValueOr: ResultT = EitherT(this.runWithRecovery)

    protected def run: FetchResult

    protected def runWithRecovery: Result = {
      val f: Result = run map Right[DatabaseError, R]

      f recover {
        case _: java.util.NoSuchElementException => {
          val info = query.getDumpInfo.toString
          Left(NotFound(info))
        }

        case NonFatal(e) => Left(QueryError(e, query.getDumpInfo.toString))
      }
    }

    def fetchOpt: Future[Option[R]] = this.fValueOr.toOption.value

    def fetch: FetchResult = fetchOpt.map(_.get)
  }

  case class WriteQuery[T](protected val query: WriteQueryT[T]#ActionType)(
      implicit val ec: ExecutionContext,
      val db: ReadWriteDB
  ) extends HandyQuery[WriteQueryT[T]] {
    override def run = db.runMaster(query)
  }

  case class ReadQuery[T](protected val query: ReadQueryT[T]#ActionType)(
      implicit val ec: ExecutionContext,
      val db: ReadWriteDB
  ) extends HandyQuery[ReadQueryT[T]] {
    override def run: FetchResult = db.runReplica(query)
  }

  case class RawQuery[T](protected val query: RawQueryT[T]#ActionType)(
      implicit val ec: ExecutionContext,
      val db: ReadWriteDB
  ) extends HandyQuery[RawQueryT[T]] {
    import Utils.RwDummies._
    override def run: FetchResult = db.runMaster(query)
  }

  case class RawQueryOnReplica[T](protected val query: RawQueryT[T]#ActionType)(
      implicit val ec: ExecutionContext,
      val db: ReadWriteDB
  ) extends HandyQuery[RawQueryT[T]] {
    override def run: FetchResult = db.runRawReplica(query)
  }

}
