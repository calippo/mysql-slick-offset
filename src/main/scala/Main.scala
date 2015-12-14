import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{ ActorSystem, Actor, ActorRef, Props }
import scala.concurrent.Future

import scala.util.Random

import slick.driver.MySQLDriver.api._
import java.util.Date


object Main extends App
  with io.buildo.base.ConfigModule
  with io.buildo.base.IngLoggingModule {

  lazy val projectName = "banksealer"
  val log = logger("banksealer")
  override def logsEnabled(name: String, level: io.buildo.ingredients.logging.Level) = true
  lazy val conf = com.typesafe.config.ConfigFactory.load()

  implicit val system = ActorSystem(projectName)

  (for {
    _ <- DBModule.dropTables
    _ <- DBModule.createDb
  } yield ()) map { _ =>
    test.run(system)
  } onComplete {
    case scala.util.Failure(failure) => println(failure)
    case _ => ()
  }
}

object test {
  var txns_ = Set.empty[Transaction]
  var completeTxns_ = Set.empty[Transaction]

  def run(system: ActorSystem): Unit = {
     system.actorOf(Props(new Scheduler(insertTransactions, every = 1, start = 0)))
     system.actorOf(Props(new Scheduler(removeSomeTxns, every = 3, start = 10)))
     system.actorOf(Props(new Scheduler(queryTable, every = 2, start = 5)))
  }

  def removeSomeTxns(): Future[Unit] = data.removeSomeTxns map { _ =>
    println("[DELETE]")
  }

  def insertTransactions(): Future[Unit] = data.insertTransactions map { _ =>
    println("[INSERT]")
  }

  def queryTable(): Future[Unit] = {
    val res: Future[Unit] = (for {
      txns <- data.getNextChunkFromTransactions
      txnsComplete <- data.getNextChunkFromCompleteTransactions
    } yield compare(txns, txnsComplete))

    Future(res onComplete {
      case scala.util.Failure(f) => println(f)
      case _ => ()
    })
  }

  def compare(txns: Set[Transaction], txnsComplete: Set[Transaction]): Unit = {
    txns_ ++=  txns
    completeTxns_ ++= txnsComplete
    if (txns_ == completeTxns_) println("[ALIGNED] OK")
    else {
      println("[ALIGNED] NOT OK")
      println(s"$txns\n$txnsComplete")
      println(s"${txns_.map(_.id).toSeq.sorted}\n${completeTxns_.map(_.id).toSeq.sorted}")
    }
  }
}

object data {
  var inserted = 0
  var completeTransactionsOffset = 0
  val takeNumber = 4

  def getNextChunkFromTransactions: Future[Set[Transaction]] =
    DBModule.db.run(
      (for {
        latestId <- LatestIds.result
        myid = latestId.headOption match {
          case Some(id) => id
          case None => LatestId(0, new Date(0).getTime, 0)
        }
        countUpToNow <- Transactions.filter(_.datetime < myid.lastDate).length.result
        new_offset: Int = myid.offset - (myid.count - countUpToNow)
        txns <- Transactions.drop(new_offset).take(takeNumber).result

        now: Long = new Date().getTime - 1000
        new_count <- Transactions.filter(_.datetime < now).length.result
        _ <- LatestIds.delete
        _ <- LatestIds += LatestId(new_offset + txns.length, now, new_count)
      } yield txns)
    .transactionally) map (_.toSet)

  def getNextChunkFromCompleteTransactions: Future[Set[Transaction]] =
    DBModule.db.run(
       CompleteTransactions.drop(completeTransactionsOffset).take(takeNumber).result
    ) map { v =>
      completeTransactionsOffset += v.length
      v.toSet
    }

  def removeSomeTxns = DBModule.db.run(
    for {
      min <- Transactions.map(_.id).min.result
      _ <- Transactions.filter(_.id === min).delete
    } yield ()
  )

  def insertTransactions = {
    inserted += 1
    val newTransaction = Transaction(
      id = 0,
      datetime = new Date().getTime,
      content = Random.alphanumeric.take(10).mkString)

    DBModule.db.run((for {
      _ <- Transactions += newTransaction
      _ <- CompleteTransactions += newTransaction
    } yield ()))
  }
}


case class LatestId(offset: Int, lastDate: Long, count: Int)
object LatestIds extends TableQuery[LatestIds](new LatestIds(_))
class LatestIds(tag: Tag) extends Table[LatestId](tag, "latestIds") {
  def offset = column[Int]("offset")
  def lastDate = column[Long]("lastDate")
  def count = column[Int]("count")

  def * = (offset, lastDate, count) <> (LatestId.tupled, LatestId.unapply)
}

case class Transaction(id: Int, datetime: Long, content: String)
object Transactions extends TableQuery[Transactions](new Transactions(_))
class Transactions(tag: Tag) extends Table[Transaction](tag, "transaction") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def datetime = column[Long]("datetime")
  def content = column[String]("content")

  def * = (id, datetime, content) <> (Transaction.tupled, Transaction.unapply)
}

object CompleteTransactions extends TableQuery[CompleteTransactions](new CompleteTransactions(_))
class CompleteTransactions(tag: Tag) extends Table[Transaction](tag, "complete_transactions") {

  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def datetime = column[Long]("datetime")
  def content = column[String]("content")

  def * = (id, datetime, content) <> (Transaction.tupled, Transaction.unapply)
}

object DBModule {
  import com.mysql.jdbc.jdbc2.optional.MysqlDataSource
  import slick.driver.MySQLDriver.api.Database
  import slick.jdbc.hikaricp.HikariCPJdbcDataSource

  val db = Database.forConfig(s"banksealer.core.db")
  def createDb: Future[Unit] = db.run(Transactions.schema.create >> CompleteTransactions.schema.create >> LatestIds.schema.create)
  def dropTables: Future[Unit] = db.run(Transactions.schema.drop >> CompleteTransactions.schema.drop >> LatestIds.schema.drop)
}

class Scheduler(f: () => Future[Unit], every: Double, start: Double) extends Actor {
  val tick = context.system.scheduler.schedule(start seconds, every seconds, self, Tick)
  case object Tick

  override def postStop() = tick.cancel()
  def receive = {
    case Tick => {
      try {
        f()
      } catch {
        case e: Throwable => println(e)
      }
    }
  }
}
