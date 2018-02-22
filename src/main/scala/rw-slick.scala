import Utils.RwDummies._
import cats.data.EitherT
import cats.implicits._
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.util.DumpInfo

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal

package object RwSlick {

  type FileAddress = String
  type Line        = Int

  type DebuggingInfo = (FileAddress, Line)

  sealed trait BaseError

  type AsyncResultT[T] = EitherT[Future, BaseError, T]

  sealed trait DatabaseError extends BaseError {
    def dumpInfo: DumpInfo
    def debuggingInfo: DebuggingInfo

    override def toString = {
      s"""Query crashed with following information:
  File: ${debuggingInfo._1}
  Line: ${debuggingInfo._2}
And following slick info: ${dumpInfo.toString}""".stripMargin
    }
  }

  case class NotFound(dumpInfo: DumpInfo)(val debuggingInfo: DebuggingInfo)                 extends DatabaseError
  case class QueryError(e: Throwable, dumpInfo: DumpInfo)(val debuggingInfo: DebuggingInfo) extends DatabaseError

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
  type MixedQueryT[R] = MyDataBaseIO[R, Effect.Read with Effect.Write]

  trait DatabaseHandler {
    def run[R](query: BaseQueryT[R]): Future[R]
  }

  class ReadWriteDB(
      writerDb: DatabaseHandler,
      readerDB: DatabaseHandler
  )(implicit executionContext: ExecutionContext) {

    def runReplica[R](query: ReadQueryT[R]#ActionType) = readerDB.run(query)

    def runMaster[R](query: WriteQueryT[R]#ActionType)                         = writerDb.run(query)
    def runMaster[R](query: RawQueryT[R]#ActionType)(implicit dummy: Dummy1)   = writerDb.run(query)
    def runMaster[R](query: MixedQueryT[R]#ActionType)(implicit dummy: Dummy2) = writerDb.run(query)

    def run[R](query: ReadQueryT[R]#ActionType)(implicit dummy: Dummy1)  = runReplica(query)
    def run[R](query: WriteQueryT[R]#ActionType)(implicit dummy: Dummy2) = runMaster(query)
    def run[R](query: RawQueryT[R]#ActionType)(implicit dummy: Dummy3)   = runMaster(query)
    def run[R](query: MixedQueryT[R]#ActionType)(implicit dummy: Dummy4) = runMaster(query)

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
    def toQuery[R](query: MixedQueryT[R]#ActionType)(implicit ec: ExecutionContext, db: ReadWriteDB, dummy: Dummy4) =
      MixedQuery(query)

    trait QueryWrapper[R] {
      type ReturnType

      def query: HandyQuery[R]
      def dbError(error: RwSlick.BaseError): ReturnType

      def runQuery(
          implicit ec: ExecutionContext,
          fileDbg: sourcecode.File,
          lineDbg: sourcecode.Line
      ): EitherT[Future, ReturnType, R] = query.fValueOr(fileDbg, lineDbg).leftMap(dbError)
    }
  }

  sealed trait HandyQuery[R] extends AnyRef {
    type QueryT <: DataBaseIO

    final type FetchResult = Future[R]
    final type Result      = Future[Either[BaseError, R]]
    final type ResultT     = AsyncResultT[R]

    def query: QueryT#ActionType

    def db: ReadWriteDB
    implicit def ec: ExecutionContext
    protected def run: FetchResult

    def fValueOr(implicit fileDbg: sourcecode.File, lineDbg: sourcecode.Line): ResultT = {
      EitherT {
        val f: Result              = this.fetch map (_.asRight)
        val dbgInfo: DebuggingInfo = (fileDbg.value, lineDbg.value)

        f recover {
          case _: java.util.NoSuchElementException =>
            NotFound(query.getDumpInfo)(dbgInfo).asLeft
          case NonFatal(e) =>
            QueryError(e, query.getDumpInfo)(dbgInfo).asLeft
        }
      }
    }

    def fetchOpt: Future[Option[R]] = this.fValueOr.toOption.value
    lazy val fetch: FetchResult     = this.run
  }

  trait HandyWrite[T] extends HandyQuery[T] {
    type QueryT = WriteQueryT[T]
  }

  trait HandyRead[T] extends HandyQuery[T] {
    type QueryT = ReadQueryT[T]
  }

  trait HandyMixed[T] extends HandyQuery[T] {
    type QueryT = MixedQueryT[T]
  }

  trait HandyRaw[T] extends HandyQuery[T] {
    type QueryT = RawQueryT[T]
  }

  case class WriteQuery[T](query: WriteQueryT[T]#ActionType)(
      implicit val ec: ExecutionContext,
      val db: ReadWriteDB
  ) extends HandyWrite[T] {
    override def run = db.runMaster(query)
  }

  case class ReadQuery[T](query: ReadQueryT[T]#ActionType)(
      implicit val ec: ExecutionContext,
      val db: ReadWriteDB
  ) extends HandyRead[T] {
    override def run: FetchResult = db.runReplica(query)
  }

  case class MixedQuery[T](query: MixedQueryT[T]#ActionType)(
      implicit val ec: ExecutionContext,
      val db: ReadWriteDB
  ) extends HandyMixed[T] {
    import Utils.RwDummies._
    override def run: FetchResult = db.runMaster(query)
  }

  case class RawQuery[T](query: RawQueryT[T]#ActionType)(
      implicit val ec: ExecutionContext,
      val db: ReadWriteDB
  ) extends HandyRaw[T] {
    import Utils.RwDummies._
    override def run: FetchResult = db.runMaster(query)
  }

  case class RawQueryOnReplica[T](query: RawQueryT[T]#ActionType)(
      implicit val ec: ExecutionContext,
      val db: ReadWriteDB
  ) extends HandyRaw[T] {
    override def run: FetchResult = db.runRawReplica(query)
  }

}
