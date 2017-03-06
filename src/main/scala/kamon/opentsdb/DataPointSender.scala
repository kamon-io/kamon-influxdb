package kamon.opentsdb

import akka.actor.ActorSystem
import akka.event.Logging
import com.stumbleupon.async.Callback
import net.opentsdb.core.TSDB
import net.opentsdb.utils.Config
import org.hbase.async.HBaseClient

import scala.collection.JavaConverters._

/**
  * A datapoint to be committed to OpenTSDB
  */
case class DataPoint(metric : String, tags : Map[String, String], timestamp : Long, value : AnyVal)

/**
  * Implementing classes can store [[DataPoint]] in OpenTSDB
  */
trait DataPointSender {
   def shutdown(): Unit

   def appendPoint(point : DataPoint)

   def flush()
}

/**
  * An [[DataPointSender]] implmentation that writes directly to HBase using the [[TSDB]] api
  */
class DirectDataPointSender(system: ActorSystem, quorum : String) extends DataPointSender {
   val log = Logging(system, classOf[DirectDataPointSender])
   val db = new TSDB(new HBaseClient(quorum), new Config(false))
   db.getConfig.setAutoMetric(true)

   override def appendPoint(point: DataPoint): Unit = {
      val deferred = point.value match {
         case value : Double => db.addPoint(point.metric, point.timestamp, value, point.tags.asJava)
         case value : Long => db.addPoint(point.metric, point.timestamp, value, point.tags.asJava)
      }

      deferred.addErrback(new Callback[Unit, Object] {
         override def call(arg: Object): Unit = {
            log.warning(arg.toString)
         }
      })
   }

   override def flush(): Unit = {
      db.flush().join()
   }

   override def shutdown(): Unit = {
      flush()
      db.shutdown().join()
   }
}