# HandyRwSlick
Write slick queries with cats.data.EitherT[Future, DatabaseError, R] (R being result) and replica support on READ queries.

# Why?
In Snapptrip.com, we were doing pretty cool stuffs:
1. Scaling postgreSQL database by dividing database queries into multiple forms of actions and having READ queries in replication databases.
http://danielwestheide.com/blog/2015/06/28/put-your-writes-where-your-master-is-compile-time-restriction-of-slick-effect-types.html
2. A nicer error handling.

# How?
1. Scalability
You need to declare two different `DatabaseHandler`s, one for "READ" actions and one for other actions.
This might seem naive but since that's all we needed so it is prefect for our goal.

```scala
  class ReplicaDB @Inject()(
      @NamedDatabase("replica")
      protected val dbConfigProvider: DatabaseConfigProvider
  )(implicit ec: ExecutionContext)
      extends HasDatabaseConfigProvider[JdbcProfile] with DatabaseHandler {

    override def run[R](query: BaseQueryT[R]) =
      db.run[R](query)
  }
  
    class MasterDB @Inject()(
      @NamedDatabase("master")
      protected val dbConfigProvider: DatabaseConfigProvider
  )(implicit ec: ExecutionContext)
      extends HasDatabaseConfigProvider[JdbcProfile] with DatabaseHandler {

    override def run[R](query: BaseQueryT[R]) =
      db.run[R](query)
  }
  
  class DatabaseIO @Inject()(
    MasterDB: masterDB,
    ReplicaDB: replicaDB
  )(implicit ec: ExecutionContext) extends ReadWriteDB(masterDB, replicaDB)
  
  class UserRepo @Inject()(DatabaseIO: db)(implicit ec: ExecutionContext) {
    def create(user: User) = userTable.insertOrUpdate(user)
    def findBy(id: Int) = userTable.filter(_.id === id).result.head
    
    def createIO = db.run(this.create _)
    def findByIO = db.run(this.findBy _)
  }
```

2. So far we did not mention any specific approach regarding handling errors.
You should have heard about `cats` library by now if not I don't know what to say.
Using EitherT and stacking Either and Future (http://eed3si9n.com/herding-cats/monad-transfomers.html) we can write clean for-comprehensions.
- First wrap your queries around a HandyQuery with `toQuery` functionality which is going to provide following interface for you:
```scala
  class UserRepo @Inject()(DatabaseIO: db)(implicit ec: ExecutionContext) {
    implicit val dbIO: ReadWriteDB = db
    
    def create(user: User) = toQuery { 
      userTable.insertOrUpdate(user)
    }

    def findBy(id: Int) = toQuery { 
      userTable.filter(_.id === id).result.head
    }
  }
```
And you can use the queries with `userRepo.findBy(1).runQuery` in order to run the database action and
it will either is going to get complete or will return an error which has the reason and the line it was caused.

# A note about this project:
I haven't touched a single line of this library in years! Obviously, Today I'd write it in a different way or I'd might not even write it at all. It served its purpose and now I just have it in my resume! Doesn't mean I am proud of it.

# License
MIT - credit to Snapptrip.com
