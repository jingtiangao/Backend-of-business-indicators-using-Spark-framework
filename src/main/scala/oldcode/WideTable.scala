package oldcode
import java.sql.Date
import java.sql.Timestamp

import org.apache.spark.sql.types._
import org.apache.spark.sql.{Column, DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions._
/**
  * Created by renguifu on 2016/9/29.
  */
object WideTable {
  def typeTransform(df:DataFrame,field:String,newtype:String):DataFrame={
    df.withColumn(field,df(field).cast(newtype))
  }
  def typeTransform(df:DataFrame,field:String):DataFrame={
    df.withColumn(field,df(field).cast(TimestampType))
  }
  def typeTransformToDatestamp(df:DataFrame,field:String):DataFrame={
    def replaceString(date:String):String={
      if(date==null){
        date
      } else {
        date.replace("/","-")
      }
    }
    val replaceFun=udf(replaceString _)
    val df1=df.withColumn(field,replaceFun(col(field)))
    typeTransform(df1,field,"timestamp")
  }
  def getvaliddate(df:DataFrame, date1:String, date2:String, newfied:String):DataFrame={
    def max1(rows:Row):Timestamp={
      if (rows.isNullAt(0) && !rows.isNullAt(1)){
        rows.getTimestamp(1)
      }else if (rows.isNullAt(1) && !rows.isNullAt(0)){
        rows.getTimestamp(0)
      }else if (rows.isNullAt(0) && rows.isNullAt(1)){
        null
      } else if(rows.getTimestamp(0).after(rows.getTimestamp(1))){
        rows.getTimestamp(0)
      }else{
        rows.getTimestamp(1)
      }
    }
    val maxFuction=udf(max1 _)
    df.withColumn(newfied,maxFuction(struct(date1,date2)))
  }
  //  根据prpcopymain批改次数是否为0，联共标志和联共比例生成一列原始保费

  def getvaliddate3(df:DataFrame,date1:String,date2:String,tag:String,validdate:String):DataFrame={
    def getvalid(date3:TimestampType,date4:TimestampType,tags:BooleanType)={
      if(tags.toString.equals("true")){
        date3
      }else{
        date4
      }

    }
    val getvalidFunc=udf(getvalid _)
    df.withColumn(validdate,getvalidFunc(col(date1),col(date2),col(tag)))

  }
  def main(args: Array[String]) {
    val spark = SparkSession.builder().appName("wideTable").getOrCreate()
    val prpcmain = spark.read.json("/data1/data/prpcmain2.json").filter(row=>row.length==9)
    val prpphead = spark.read.json("/data1/data/prpphead2.json")
    val prppfee = spark.read.json("/data1/data/prppfee2.json").filter(row=>row.length==4)
    val prpcprofit = spark.read.json("/data1/data/prpcprofit2.json").filter(row=>row.length==3)
    val prpcitem_car = spark.read.json("/data1/data/prpcitem_car2.json").filter(row=>row.length==2)
    val prppitemkind = spark.read.json("/data1/data/prppitemkind2.json").filter(row=>row.length==6)

    val prpphead1 = prpphead.filter(row=>row.length==7)toDF("applyno", "endortype1", "policyno", "riskcode1", "underwriteenddate1", "underwriteflag1", "validdate1")
    val prpphead_date1=typeTransformToDatestamp(prpphead1,"underwriteenddate1")
    val prpphead_date=typeTransformToDatestamp(prpphead_date1,"validdate1")

    val prpcmain_date1=typeTransformToDatestamp(prpcmain,"startdate")
    val prpcmain_date2=typeTransformToDatestamp(prpcmain_date1,"enddate")
    val prpcmain_date=typeTransformToDatestamp(prpcmain_date2,"underwriteenddate")
    val prpcmain_validate=getvaliddate(prpcmain_date,"startdate","underwriteenddate","validdate")
    val prpcmain1 = prpcmain_validate.join(prpphead_date, Seq("policyno"), "left_outer")

    val prppfee1 = prppfee.toDF("applyno", "chgamount1", "chgpremium1", "currency")

    val prpcmain2 = prpcmain1.join(prppfee1, Seq("applyno"), "left_outer")

    val prpcmain3 = prpcmain2.join(prpcprofit, Seq("proposalno"), "left_outer")

    val prpcmain4 = prpcmain3.join(prpcitem_car, Seq("proposalno"), "left_outer")

    val prppmain = spark.read.json("/data1/data/prppmain2.json").filter(row=>row.length==5)

    val prppmain1 = prppmain.toDF("applyno", "chgpremium3", "coinsflag1", "othflag2", "riskcode2")

    val prpcmain5 = prpcmain4.join(prppmain1, Seq("applyno"), "left_outer")

    val prppitemkind1 = prppitemkind.toDF("applyno", "calculateflag", "chgamount2", "chgpremium2", "currency1", "policyno")
    val prpcmain6 = prpcmain5.join(prppitemkind1, Seq("applyno", "policyno"), "left_outer")
    prpcmain6.repartition(1).write.parquet("/data1/data/widetable/")
  }
}
