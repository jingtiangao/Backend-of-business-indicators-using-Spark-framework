package oldcode
import java.sql.Timestamp

import org.apache.spark.sql.types._
import org.apache.spark.sql.{Column, DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions._
/**
  * Created by renguifu on 2016/9/29.
  */
object SingleWidetable {
  def typeTransform(df: DataFrame, field: String, newtype: String): DataFrame = {
    df.withColumn(field, df(field).cast(newtype))
  }

  //  类型转换为timestamp
  def typeTransformToDatestamp(df: DataFrame, field: String): DataFrame = {
    def replaceString(date: String): String = {
      if (date == null) {
        date
      } else {
        date.replace("/", "-")
      }
    }
    val replaceFun = udf(replaceString _)
    val df1 = df.withColumn(field, replaceFun(col(field)))
    typeTransform(df1, field, "timestamp")
  }

  //  两个日期取大，求出有效日期
  def getvaliddate(df: DataFrame, date1: String, date2: String, newfied: String): DataFrame = {
    def max1(rows: Row): Timestamp = {
      if (rows.isNullAt(0) && !rows.isNullAt(1)) {
        rows.getTimestamp(1)
      } else if (rows.isNullAt(1) && !rows.isNullAt(0)) {
        rows.getTimestamp(0)
      } else if (rows.isNullAt(0) && rows.isNullAt(1)) {
        null
      } else if (rows.getTimestamp(0).after(rows.getTimestamp(1))) {
        rows.getTimestamp(0)
      } else {
        rows.getTimestamp(1)
      }
    }
    val maxFuction = udf(max1 _)
    df.withColumn(newfied, maxFuction(struct(date1, date2)))

  }
  def transFlag(df:DataFrame,otherFlag:String,index:Int,newFlag:String): DataFrame ={
    def indexFlag(otherflag:String):String={
      if (otherflag==null)
        null
      else
        otherflag(index).toString
    }
    val indexFlagFunction=udf(indexFlag _)
    df.withColumn(newFlag,indexFlagFunction(col(otherFlag)))
  }

  //  根据prpcopymain批改次数是否为0，联共标志和联共比例生成一列原始保费


  def main(args: Array[String]) {
    val spark = SparkSession.builder().appName("wideTable").getOrCreate()

  }
}
