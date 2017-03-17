package kamon.opentsdb

import com.stumbleupon.async.Callback
import net.opentsdb.core.TSDB
import net.opentsdb.utils.Config
import org.hbase.async.HBaseClient
import org.slf4j.LoggerFactory

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
  * An [[DataPointSender]] implementation that writes directly to HBase using the [[TSDB]] api
  */
class DirectDataPointSender(quorum : String) extends DataPointSender {
   val logger = LoggerFactory.getLogger(classOf[DirectDataPointSender])

   // TODO Need some way to send in configuration
   val db = new TSDB(new HBaseClient(quorum), new Config(false))
   db.getConfig.setAutoMetric(true)

   override def appendPoint(point: DataPoint): Unit = {
      logger.debug(s"Storing '${point.value}' for metric '${point.metric}' with value '${point.tags}'")
      val deferred = point.value match {
         case value : Double => db.addPoint(point.metric, point.timestamp, value, point.tags.asJava)
         case value : Long => db.addPoint(point.metric, point.timestamp, value, point.tags.asJava)
      }

      deferred.addErrback(new Callback[Unit, Object] {
         override def call(arg: Object): Unit = {
            logger.warn(arg.toString)
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