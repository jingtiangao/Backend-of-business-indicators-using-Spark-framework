import java.sql.Timestamp

import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import java.text.SimpleDateFormat
/**
  * Created by renguifu on 2016/9/29.
  */
object SingleWidetable {
  def typeTransform(df: DataFrame, field: String, newtype: String): DataFrame = {
    df.withColumn(field, df(field).cast(newtype))
  }
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
// 从一个字段拉伸多个字段
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
  def loadJson(path:String,spark:SparkSession):DataFrame={
    spark.read.json(path)
  }
  def loadCsv(path:String,delimter:String,spark:SparkSession,head:Boolean=false):DataFrame={
    spark.read.option("seq",delimter).option("header",head).csv(path)
  }
//  从一个标志字段抽取出来单独作为一个字段
  def getFlag(otherflag:String,index:Int,trueflag1:String*):Int={
    if (otherflag==null)
      0
    else if (trueflag1.contains(otherflag(index).toString.trim())) 1
    else 0
  }
//  空值为个人，有值为团车
  def getTuancheOrGeren(line:String):Int={
    if (line!=null) 0 else 1
  }
//获取新、续、转标志
  def getNewOrXuOrZhuang(otherflag:String,index:Int,newFlag:String,xuFlag:String,zhuangFlag:String):Int= {
    if (otherflag == null)
      0
    else if (otherflag(index).toString.equals(newFlag))
      0
    else if (otherflag(index).toString.equals(xuFlag)) 1
    else if (otherflag(index).toString.equals(zhuangFlag)) 2
    else 0
  }
//  把String类型转换为timestamp类型
  def getTimestamp(date: String): Timestamp = {
    if (date==null)   return null
    val string=date.replace("/","-")
    Timestamp.valueOf(string)

  }
//  求出两个timestamp中最大的日期
  def getMaxTimestamp(date1:String,date2:String):Timestamp={
    val timestamp1=getTimestamp(date1)
    val timestamp2=getTimestamp(date2)
    if (timestamp1==null && timestamp2!=null) {
      timestamp2
    }else if (timestamp1 !=null && timestamp2==null){
      timestamp1
    }else if(timestamp1 ==null && timestamp2 ==null){
      null
    } else if (timestamp1.after(timestamp2)) timestamp1
    else timestamp2
  }
//  取出prpphead中的不同批改类型对应的值,来作为判断标志,求出退保标志
  def containsFlag(changeString:String,trueflag1:String,trueflag2:String):Int={
     if (changeString.contains(trueflag1) || changeString.contains(trueflag2))  1 else 0
  }
// 取出prpphead中的不同批改类型对应的值，来判断标志，求出保单注销标志
  def inFlag(edortype:String,trueFlag:Set[String]):Int={
    if (trueFlag.contains(edortype)) 1 else 0
  }
//  取出prpphead中的不同批改类型对应的值，来判断批减非退标志
  def notInFlag(edortype:String,trueFlag:Set[String]):Int={
    if (!trueFlag.contains(edortype.trim())) 1 else 0
  }
//  起保日期和核保日期取大在统计范围内
  def filterInTime(startDate:Timestamp,endDate:Timestamp,policyvaliddate:Timestamp):Boolean={
    if (policyvaliddate==null) false
    else if (policyvaliddate.after(startDate) && policyvaliddate.before(endDate)) true else false
  }

// prpphead的生效日期和核批日期取大在统计范围内，起保日期小于统计结束时间
  def filterPrppTime(startDate:Timestamp,endDate:Timestamp,maxValidAndWrite:Timestamp,policyStartDate:Timestamp):Boolean={
    if (policyStartDate==null) false
    else if (filterInTime(startDate,endDate,maxValidAndWrite) && policyStartDate.before(endDate)) true else false
  }
// 生效日期和核批日期取大且在统计范围内，核批日期、生效日期均大于等于起保日期，上述三个日期都在统计范围内
  def filterTuibaoTime(startDate:Timestamp,endDate:Timestamp,maxValideAndWrite:Timestamp,validdate:Timestamp,underwriteenddate:Timestamp,policyStartDate:Timestamp):Boolean={
    if (underwriteenddate==null || policyStartDate==null || validdate==null) false
    else if (filterInTime(startDate,endDate,maxValideAndWrite) && filterInTime(startDate,endDate,underwriteenddate) && filterInTime(startDate,endDate,validdate) && filterInTime(startDate,endDate,policyStartDate) && underwriteenddate.after(policyStartDate) && validdate.after(policyStartDate)) true else false
  }
//  根据联共标志来判断是否为非联共，当为非联共时，需要乘以联共比例
  def renewPremiumOrAmout(premiumOramout:Double,coinsflag:Int,coinsrate:Double): Double ={
    if (coinsflag==1) premiumOramout*coinsrate else premiumOramout
  }
// 获取原始保单保费,核保标志通过，注销标志未注销，已收费
  def getRawpremium(premiumOramout:Double,coinsflag:Int,coinsrate:Double,endorsetimes:Double,underwriteflag:Int,chargeflag:Int,policycancelflag:Int,startDate:Timestamp,endDate:Timestamp,policyvaliddate:Timestamp):Double={
    if (endorsetimes==0 && underwriteflag==1 && chargeflag==1 && policycancelflag==1 && filterInTime(startDate,endDate,policyvaliddate)) renewPremiumOrAmout(premiumOramout,coinsflag,coinsrate) else 0
  }

//  获取批增次数 保费变化量大于0，核批标志通过，已收费
  def getIncreasecount(chgpremium:Double,underwriteflag:Int,chargeflag:Int,startDate:Timestamp,endDate:Timestamp,maxValidAndWrite:Timestamp,policyStartDate:Timestamp):Int={
    if (chgpremium>0 && underwriteflag==1 && chargeflag==1 && filterPrppTime(startDate,endDate,maxValidAndWrite,policyStartDate)) 1 else 0
  }
//  获取批减次数 保费变化量小于0，核批标志通过，已收费
  def getDecreasecount(chgpremium:Double,underwriteflag:Int,chargeflag:Int,startDate:Timestamp,endDate:Timestamp,maxValidAndWrite:Timestamp,policyStartDate:Timestamp):Int={
    if (chgpremium<0 && underwriteflag==1 && chargeflag==1 && filterPrppTime(startDate,endDate,maxValidAndWrite,policyStartDate)) 1 else 0
  }
//  获取批增保费，保费变化量大于0，核批标志通过，已收费，批改类型为（19，58）
  def getIncreasepremium(chgpremium:Double,coinsflag:Int,coinsrate:Double,underwriteflag:Int,chargeflag:Int,startDate:Timestamp,endDate:Timestamp,maxValidAndWrite:Timestamp,policyStartDate:Timestamp):Double={
    if (chgpremium>0  && underwriteflag==1 && chargeflag==1 && filterPrppTime(startDate,endDate,maxValidAndWrite,policyStartDate)) renewPremiumOrAmout(chgpremium,coinsflag,coinsrate) else 0
  }
//  获取批减保费，保费变化量小于0，核批标志通过，已收费，批改类型为（19，58）
  def getDecreasepremium(chgpremium:Double,coinsflag:Int,coinsrate:Double,underwriteflag:Int,chargeflag:Int,startDate:Timestamp,endDate:Timestamp,maxValidAndWrite:Timestamp,policyStartDate:Timestamp):Double={

    if (chgpremium<0 && underwriteflag==1 && chargeflag==1 && filterPrppTime(startDate,endDate,maxValidAndWrite,policyStartDate)) renewPremiumOrAmout(chgpremium,coinsflag,coinsrate) else 0
  }
//  获取批增保额，保额变换量大于0，核批标志已通过，已收费，计算保额
  def getIncreaseamount(chgamount:Double,coinsflag:Int,coinsrate:Double,underwriteflag:Int,chargeflag:Int,calculateflag:String,startDate:Timestamp,endDate:Timestamp,maxValidAndWrite:Timestamp,policyStartDate:Timestamp):Double={
    if (calculateflag==null) 0
    else if(chgamount>0 && calculateflag.equals("Y") && underwriteflag==1 && chargeflag==1 && filterPrppTime(startDate,endDate,maxValidAndWrite,policyStartDate)) {
      renewPremiumOrAmout(chgamount,coinsflag,coinsrate)
    }else 0
  }
//  获取批减保额，保额变化量小于0，核批标志已通过，已收费，计算保额
  def getDecreaseamount(chgamount:Double,coinsflag:Int,coinsrate:Double,underwriteflag:Int,chargeflag:Int,calculateflag:String,startDate:Timestamp,endDate:Timestamp,maxValidAndWrite:Timestamp,policyStartDate:Timestamp):Double={
    if (calculateflag==null) 0
    else if(chgamount<0 && calculateflag.equals("Y") && underwriteflag==1 && chargeflag==1 && filterPrppTime(startDate,endDate,maxValidAndWrite,policyStartDate)) {
      renewPremiumOrAmout(chgamount,coinsflag,coinsrate)
    }else 0
  }
//  获取批改退保次数，核批标志通过，已收费，退保标志：是，时间符合
  def getTuibaocount(underwriteflag:Int,chargeflag:Int,tuibaoflag:Int,startDate:Timestamp,endDate:Timestamp,maxValideAndWrite:Timestamp,validdate:Timestamp,underwriteenddate:Timestamp,policyStartDate:Timestamp):Int={
    if (underwriteflag==1 && chargeflag==1 && tuibaoflag==1 && filterTuibaoTime(startDate,endDate,maxValideAndWrite,validdate,underwriteenddate,policyStartDate)) 1 else 0
  }
// 获取批改退保保额（退保保额），核批标志通过，已收费，退保标志：是，时间符合
  def getTuibaoamount(chgamount:Double,coinsflag:Int,coinsrate:Double,underwriteflag:Int,chargeflag:Int,tuibaoflag:Int,calculateflag:String,startDate:Timestamp,endDate:Timestamp,maxValideAndWrite:Timestamp,validdate:Timestamp,underwriteenddate:Timestamp,policyStartDate:Timestamp):Double={
    if (calculateflag==null) 0
    else if (underwriteflag==1 && calculateflag.equals("Y") && chargeflag==1 && tuibaoflag==1 && filterTuibaoTime(startDate,endDate,maxValideAndWrite,validdate,underwriteenddate,policyStartDate)) renewPremiumOrAmout(chgamount,coinsflag,coinsrate)
    else 0

  }
//  获取批改退保保费（退保保费），核批标志通过，已收费，退保标志：是，时间符合
  def getTuibaopremium(chgpremium:Double,coinsflag:Int,coinsrate:Double,underwriteflag:Int,chargeflag:Int,tuibaoflag:Int,startDate:Timestamp,endDate:Timestamp,maxValideAndWrite:Timestamp,validdate:Timestamp,underwriteenddate:Timestamp,policyStartDate:Timestamp):Double={
    if (underwriteflag==1 && chargeflag==1 && tuibaoflag==1 && filterTuibaoTime(startDate,endDate,maxValideAndWrite,validdate,underwriteenddate,policyStartDate)) renewPremiumOrAmout(chgpremium,coinsflag,coinsrate) else 0

  }
//  获取注销保单数量(求保单数量)，核批标志通过，已收费，保单注销标志：是，时间符合
  def getPolicyCancelCount(underwriteflag:Int,chargeflag:Int,policycancelflag:Int,startDate:Timestamp,endDate:Timestamp,maxValideAndWrite:Timestamp,validdate:Timestamp,underwriteenddate:Timestamp,policyStartDate:Timestamp):Int={
    if (underwriteflag==1 && chargeflag==1 && policycancelflag==1 && filterTuibaoTime(startDate,endDate,maxValideAndWrite,validdate,underwriteenddate,policyStartDate)) 1 else 0
  }
//  获取注销保额，  核批标志通过，已收费，保单注销标志：是，是否计算保额：是，时间符合
  def getPolicyCancelAmount(chgamount:Double,coinsflag:Int,coinsrate:Double,underwriteflag:Int,chargeflag:Int,policycancelflag:Int,calculateflag:String,startDate:Timestamp,endDate:Timestamp,maxValideAndWrite:Timestamp,validdate:Timestamp,underwriteenddate:Timestamp,policyStartDate:Timestamp):Double={
    if (calculateflag==null) 0
    else if (underwriteflag==1 && calculateflag.equals("Y") && chargeflag==1 && policycancelflag==1 && filterTuibaoTime(startDate,endDate,maxValideAndWrite,validdate,underwriteenddate,policyStartDate)) renewPremiumOrAmout(chgamount,coinsflag,coinsrate)
    else 0
  }
//  获取注销保费，核批标志通过，已收费，保单注销标志：是，时间符合
  def getPolicyCancelPremium(chgpremium:Double,coinsflag:Int,coinsrate:Double,underwriteflag:Int,chargeflag:Int,policycancelflag:Int,startDate:Timestamp,endDate:Timestamp,maxValideAndWrite:Timestamp,validdate:Timestamp,underwriteenddate:Timestamp,policyStartDate:Timestamp):Double={
    if (underwriteflag==1 && chargeflag==1 && policycancelflag==1 && filterTuibaoTime(startDate,endDate,maxValideAndWrite,validdate,underwriteenddate,policyStartDate)) renewPremiumOrAmout(chgpremium,coinsflag,coinsrate) else 0
  }
//  获取批减非退保保单数量（保单数量），核批标志通过，已收费，批减非退标志：是
  def getDecreaseNotcancelcount(underwriteflag:Int,chargeflag:Int,decreaseNotcancelflag:Int,startDate:Timestamp,endDate:Timestamp,maxValideAndWrite:Timestamp,validdate:Timestamp,underwriteenddate:Timestamp,policyStartDate:Timestamp):Int={
    if (underwriteflag==1 && chargeflag==1 && decreaseNotcancelflag==1 && filterTuibaoTime(startDate,endDate,maxValideAndWrite,validdate,underwriteenddate,policyStartDate)) 1 else 0
  }
  case class Prpcopymain(applyno:String,policyno:String,startdate:java.sql.Timestamp,policyunderwriteenddate:java.sql.Timestamp,endorsetimes:Double,othflag:String,sumpremium:Double,sumamount:Double,coinsflag:Int,chargeflag:Int,policyValiddate:java.sql.Timestamp,policyunderwriteflag:Int,cancelflag:Int)
  case class Prpphead(applyno:String,underwriteflag:Int,validdate:java.sql.Timestamp,endortype:String,prppunderwriteenddate:java.sql.Timestamp,maxValiddateAndWritedate:java.sql.Timestamp,tuibaoflag:Int,policycancelflag:Int,decreaseNotcancelflag:Int)
  case class Prpcopycoins(applyno:String,coinsrate:Double)
  case class Prpcopynewtables(applyno:String,policyno:String,rawpremium:Double,increasecount:Int,decreasecount:Int,increasepremium:Double,decreasepremium:Double,increaseamount:Double,decreaseamount:Double,tuibaocount:Int,tuibaoamount:Double,tuibaopremium:Double,policycancelcount:Int,policycancelamount:Double,policycancelpremium:Double,decreasenotcancelcount:Int)
  case class Prpcmain(proposalno:String,policyno:String,riskcode:String,comcode:String,underwriteenddate:java.sql.Timestamp,startdate:java.sql.Timestamp,validdate:java.sql.Timestamp,operatedate:java.sql.Timestamp,underwriteflag:Int,chargeflag:Int,cancelflag:Int,coinsflag:Int,sumpremium:Double,sumamount:Double,stopflag:Int,inputflag:String,businessnature:String,agentcode:String,enddate:java.sql.Timestamp,continueflag:Int,othflag:String,maxOperatedateUnderwritedate:java.sql.Timestamp,maxStartdateUnderwritedate:java.sql.Timestamp,Tuancheflag:Int)
  case class Prpcitem_car(proposalno:String,carid:String,licenseno:String,vinno:String,licensetype:String,usenaturecode:String,purchaseprice:String,seatcount:String,toncount:String,exhaustscale:String,useyears:String,clausetype:String,carkindcode:String)
  case class Prpdcompany(comcode:String,comcname:String,comshortname:String,comename:String,addresscname:String,addressename:String,comtype:String,comlevel:String,comkind:String)
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName("wideTable").getOrCreate()
    import spark.implicits._
//    import spark.sqlContext.implicits._
// 统计范围
    val path1="/root/data/prpcmain.json"
    val path2="/root/data/prpccoins.json"
    val path3="/root/data/prpcitem_car"
    val path4="/root/data/prpdcompany.txt"
//    :q"2013-01-02 00:00:00.000"
//    val staticStartDate=Timestamp.valueOf("2013-01-02 00:00:00.000")
//    val staticEndDate=Timestamp.valueOf("2016-01-02 00:00:00.000")
    val staticStartDate=Timestamp.valueOf(args(0))
    val staticEndDate=Timestamp.valueOf(args(1))
    val staticStartDatepre=Timestamp.valueOf(args(0))
    val staticEndDatepre=Timestamp.valueOf(args(1))
//    导入所有关于批改信息的表
//    导入prpcopymain并且把时间处理成符合timestamp类型的字符串类型，把收费标志抽取出来，求出核保日期和起保日期的最大值
    val prpcopymain=loadJson("/root/data/prpcopymain.json",spark)
      .select("applyno","policyno","startdate","underwriteenddate","endorsetimes","othflag","sumpremium","sumamount","coinsflag","underwriteflag")
      .map(row=>Prpcopymain(row.getString(0),row.getString(1),getTimestamp(row.getString(2)),getTimestamp(row.getString(3)),row.getDouble(4),row.getString(5),row.getDouble(6),row.getDouble(7),getFlag(row.getString(8),0,"1","2"),getFlag(row.getString(5),6,"Y"),getMaxTimestamp(row.getString(2),row.getString(3)),getFlag(row.getString(9),0,"1","3"),getFlag(row.getString(5),3,"0"))).toDF()
//      .toDF("applyno","policyno","startdate","policyunderwriteenddate","endorsetimes","othflag","sumpremium","sumamount","coinsflag","chargeflag","policyValiddate","policyunderwriteflag","cancelflag")

//    导入prpphead

//  当批改类型为这些集合时，保单注销标志为1
    val cancelFlagSet=Set("19","N1")
//    当批改类型为这些集合是时，批减非退标志位1
    val decreaseCancelFlag=Set("19","N1","21","10")
    val prpphead=loadJson("/root/data/prpphead.json",spark)
      .select("applyno","underwriteflag","validdate","endortype","underwriteenddate")
      .map(row=>Prpphead(row.getString(0),getFlag(row.getString(1),0,"1","3"),getTimestamp(row.getString(2)),row.getString(3),getTimestamp(row.getString(4)),getMaxTimestamp(row.getString(2),row.getString(4)),containsFlag(row.getString(3),"21","10"),inFlag(row.getString(3),cancelFlagSet),notInFlag(row.getString(3),decreaseCancelFlag))).toDF()
//      .toDF("applyno","endordate","underwriteflag","validdate","endortype","prppunderwriteenddate","maxValiddateAndWritedate","tuibaoflag","policycancelflag","decreaseNotcancelflag")
//   prpCopymain 与prpphead关联在一起
    val prpCopymainAndhead=prpcopymain.join(prpphead,Seq("applyno"),"left_outer")

//   通过给定的统计范围对数据进行过滤
    val prpCopymainAndhead1=prpCopymainAndhead.filter(row=>filterInTime(staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("policyValiddate"))) ||
      filterPrppTime(staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("startdate"))) ||
      filterTuibaoTime(staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("validdate")),row.getTimestamp(row.fieldIndex("prppunderwriteenddate")),row.getTimestamp(row.fieldIndex("startdate"))))


//    导入prppfee 、prppitemkind 和prppcoins 关联起来
    val prppfee=loadJson("/root/data/prppfee.json",spark)
    val prppitemkind=loadJson("/root/data/prppitemkind.json",spark).select("applyno","calculateflag","currency")
    val prpcopycoins=loadJson("/root/data/prpcopycoins.json",spark).map(row=>Prpcopycoins(row.getString(0),row.getDouble(1) / 100)).toDF()
      //toDF("applyno","coinsrate")
    val prpcopytables=prpCopymainAndhead1.join(prppfee,Seq("applyno"),"left_outer").join(prppitemkind,Seq("applyno"),"left_outer").join(prpcopycoins,Seq("applyno"),"left_outer").na.fill(Map("coinsrate"->1.0,"tuibaoflag"->0,"policycancelflag"->0,"decreaseNotcancelflag"->0,"chgpremium"->0,"underwriteflag"->0,"chgamount"->0))
//     产生新的指标字段
    val prpcopynewtables=prpcopytables.map(
      row=>
        Prpcopynewtables(row.getString(row.fieldIndex("applyno")),row.getString(row.fieldIndex("policyno")),
          getRawpremium(row.getDouble(row.fieldIndex("sumpremium")),row.getInt(row.fieldIndex("coinsflag")),row.getDouble(row.fieldIndex("coinsrate")),row.getDouble(row.fieldIndex("endorsetimes")),row.getInt(row.fieldIndex("policyunderwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getInt(row.fieldIndex("cancelflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("policyValiddate"))),//获取原始保费
          getIncreasecount(row.getDouble(row.fieldIndex("chgpremium")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("startdate"))),//获取批增次数
          getDecreasecount(row.getDouble(row.fieldIndex("chgpremium")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("startdate"))),// 获取批减次数
          getIncreasepremium(row.getDouble(row.fieldIndex("chgpremium")),row.getInt(row.fieldIndex("coinsflag")),row.getDouble(row.fieldIndex("coinsrate")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("startdate"))),//获取批增保费
          getDecreasepremium(row.getDouble(row.fieldIndex("chgpremium")),row.getInt(row.fieldIndex("coinsflag")),row.getDouble(row.fieldIndex("coinsrate")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("startdate"))),//获取批减保费
          getIncreaseamount(row.getDouble(row.fieldIndex("chgamount")),row.getInt(row.fieldIndex("coinsflag")),row.getDouble(row.fieldIndex("coinsrate")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getString(row.fieldIndex("calculateflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("startdate"))), //获取批增保额
          getDecreaseamount(row.getDouble(row.fieldIndex("chgamount")),row.getInt(row.fieldIndex("coinsflag")),row.getDouble(row.fieldIndex("coinsrate")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getString(row.fieldIndex("calculateflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("startdate"))), //获取批减保额
          getTuibaocount(row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getInt(row.fieldIndex("tuibaoflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("validdate")),row.getTimestamp(row.fieldIndex("prppunderwriteenddate")),row.getTimestamp(row.fieldIndex("startdate"))),//获取退保数量
          getTuibaoamount(row.getDouble(row.fieldIndex("chgamount")),row.getInt(row.fieldIndex("coinsflag")),row.getDouble(row.fieldIndex("coinsrate")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getInt(row.fieldIndex("tuibaoflag")),row.getString(row.fieldIndex("calculateflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("validdate")),row.getTimestamp(row.fieldIndex("prppunderwriteenddate")),row.getTimestamp(row.fieldIndex("startdate"))),//获取退保保额
          getTuibaopremium(row.getDouble(row.fieldIndex("chgpremium")),row.getInt(row.fieldIndex("coinsflag")),row.getDouble(row.fieldIndex("coinsrate")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getInt(row.fieldIndex("tuibaoflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("validdate")),row.getTimestamp(row.fieldIndex("prppunderwriteenddate")),row.getTimestamp(row.fieldIndex("startdate"))),//获取退保保费
          getPolicyCancelCount(row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getInt(row.fieldIndex("policycancelflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("validdate")),row.getTimestamp(row.fieldIndex("prppunderwriteenddate")),row.getTimestamp(row.fieldIndex("startdate"))),//获取注销保单标志，最后求保单数量时需要去重
          getPolicyCancelAmount(row.getDouble(row.fieldIndex("chgamount")),row.getInt(row.fieldIndex("coinsflag")),row.getDouble(row.fieldIndex("coinsrate")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getInt(row.fieldIndex("policycancelflag")),row.getString(row.fieldIndex("calculateflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("validdate")),row.getTimestamp(row.fieldIndex("prppunderwriteenddate")),row.getTimestamp(row.fieldIndex("startdate"))),// 获取注销保额
          getPolicyCancelPremium(row.getDouble(row.fieldIndex("chgpremium")),row.getInt(row.fieldIndex("coinsflag")),row.getDouble(row.fieldIndex("coinsrate")),row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getInt(row.fieldIndex("policycancelflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("validdate")),row.getTimestamp(row.fieldIndex("prppunderwriteenddate")),row.getTimestamp(row.fieldIndex("startdate"))),//获取注销保费
          getDecreaseNotcancelcount(row.getInt(row.fieldIndex("underwriteflag")),row.getInt(row.fieldIndex("chargeflag")),row.getInt(row.fieldIndex("decreaseNotcancelflag")),staticStartDate,staticEndDate,row.getTimestamp(row.fieldIndex("maxValiddateAndWritedate")),row.getTimestamp(row.fieldIndex("validdate")),row.getTimestamp(row.fieldIndex("prppunderwriteenddate")),row.getTimestamp(row.fieldIndex("startdate")))
         )).toDF()

//      toDF("applyno","policyno","rawpremium","increasecount","decreasecount","increasepremium","decreasepremium","increaseamount","decreaseamount","tuibaocount","tuibaoamount","tuibaopremium","policycancelcount","policycancelamount","policycancelpremium","decreasenotcancelcount")

//    根据保单号进行goupby,最终产生一个保单号对应原始保单保费、批增次数、批减次数、批增保费、批减保费、批增保额、批减保额、退保数量、退保保额、退保保费、注销保单数量（max）、注销保单保额、注销保费、批减非退保单数量
    val prpcopysingletables=prpcopynewtables.groupBy("policyno").agg(Map("rawpremium"->"sum","increasecount"->"sum","decreasecount"->"sum","increasepremium"->"sum","decreasepremium"->"sum","increaseamount"->"sum","decreaseamount"->"sum","tuibaocount"->"sum","tuibaoamount"->"sum","tuibaopremium"->"sum","policycancelcount"->"max","policycancelamount"->"sum","policycancelpremium"->"sum","decreasenotcancelcount"->"max"))
      //.toDF("policyno","rawpremium","increasecount","decreasecount","increasepremium","decreasepremium","increaseamount","decreaseamount","tuibaocount","tuibaoamount","tuibaopremium","policycancelcount","policycancelamount","policycancelpremium","decreasenotcancelcount")
//      读取prpcmain中的数据，产生相应的指标
    val prpcmain1=loadJson(path1,spark).select("proposalno","policyno","riskcode","comcode","underwriteenddate","startdate","operatedate","underwriteflag","coinsflag","sumpremium","sumamount","inputflag","businessnature","agentcode","enddate","othflag","contractno").map(row=>Prpcmain(row.getString(0),row.getString(1),row.getString(2),row.getString(3),getTimestamp(row.getString(4)),getTimestamp(row.getString(5)),getMaxTimestamp(row.getString(4),row.getString(5)),getTimestamp(row.getString(6)),inFlag(row.getString(7),Set("1","3")),getFlag(row.getString(15),6,"Y"),getFlag(row.getString(15),3,"1"),getFlag(row.getString(8),0,"1","2"),row.getDouble(9),row.getDouble(10),getFlag(row.getString(15),5,"1"),row.getString(11),row.getString(12),row.getString(13),getTimestamp(row.getString(14)),getNewOrXuOrZhuang(row.getString(15),0,"0","1","2"),row.getString(15),getMaxTimestamp(row.getString(4),row.getString(6)),getMaxTimestamp(row.getString(4),row.getString(5)),getTuancheOrGeren(row.getString(16)))).toDF()//.toDF("proposalno","policyno","riskcode","comcode","underwriteenddate","startdate","validdate","operatedate","underwriteflag","chargeflag","cancelflag","coinsflag","sumpremium","sumamount","stopflag", "inputflag","businessnature","agentcode","enddate","continueflag","otherflag","maxOperatedateUnderwritedate","maxStartdateUnderwritedate")
    val prpcmain2=prpcmain1.filter(row=>filterInTime(staticStartDate,staticEndDate,row.getTimestamp(21))||filterInTime(staticStartDate,staticEndDate,row.getTimestamp(22))||filterInTime(staticStartDate,staticEndDate,row.getTimestamp(18))||filterInTime(staticStartDatepre,staticEndDatepre,row.getTimestamp(18)))
    val prpccoins=loadJson(path2,spark)
    val prpcitem_car1=spark.read.csv(path3)
    val seq=prpcitem_car1.first().getString(0)(22)
    val prpcitem_car=prpcitem_car1.map(row=>row.getString(0).split(seq)).filter(row=>row.length==13).map(arr=>Prpcitem_car(arr(0),arr(1),arr(2),arr(3),arr(4),arr(5),arr(6),arr(7),arr(8),arr(9),arr(10),arr(11),arr(12))).toDF()
    val prpdcompany1=spark.read.text(path4)
    val prpdcompany=prpdcompany1.map(row=>row.getString(0).split(seq)).filter(row=>row.length==9).map(arr=>Prpdcompany(arr(0),arr(1),arr(2),arr(3),arr(4),arr(5),arr(6),arr(7),arr(8)))

    val prpcmain3=prpcmain2.join(prpccoins,Seq("proposalno"),"left_outer").na.fill(0.5,Array("coinsrate")).join(prpcitem_car,Seq("proposalno"),"left_outer").join(prpdcompany,Seq("comcode"),"left_outer")

    def getZhibiao(row:Row):Double={
      val money=row.getDouble(0)
      val coinsflag=row.getInt(1)
      val coinsrate=row.getDouble(2)
      val underwriteflag=row.getInt(3)
      val chargeflag=row.getInt(4)
      val cancelflag=row.getInt(5)
      val date=row.getTimestamp(6)
      if (underwriteflag==1 && chargeflag==1 && cancelflag==0 && filterInTime(staticStartDate,staticEndDate,date)) renewPremiumOrAmout(money,coinsflag,coinsrate) else 0
    }
    def getZhibiao2(row:Row):Int={
      val enddate=row.getTimestamp(0)
      val underwriteflag=row.getInt(1)
      val cancelflag=row.getInt(2)
      if (filterInTime(staticStartDate,staticEndDate,enddate) && underwriteflag==1 && cancelflag==0)  1  else 0
    }
    def getYearMonth(aplly:String):String={
      if (aplly!=null) staticStartDate.toString.substring(0,7)
      else null
    }

    spark.udf.register("getYearMonth",getYearMonth _)
    spark.udf.register("getZhibiao",getZhibiao _)
    spark.udf.register("getZhibiao2",getZhibiao2 _)

    val prpcmain4=prpcmain3.withColumn("qiandanbaofei",callUDF("getZhibiao",struct("sumpremium","coinsflag","coinsrate","underwriteflag","chargeflag","cancelflag","maxOperatedateUnderwritedate")))
      .withColumn("baofeishouru",callUDF("getZhibiao",struct("sumpremium","coinsflag","coinsrate","underwriteflag","chargeflag","cancelflag","maxStartdateUnderwritedate")))
      .withColumn("baoxianjine",callUDF("getZhibiao",struct("sumamount","coinsflag","coinsrate","underwriteflag","chargeflag","cancelflag","maxStartdateUnderwritedate")))
      .withColumn("zhongbaoshuliang",callUDF("getZhibiao2",struct("enddate","underwriteflag","cancelflag")))
      .withColumn("timeYearMonth",callUDF("getYearMonth",col("policyno")))
//    与过程字段进行关联产生最中的宽表
    val singgleTable=prpcmain4.join(prpcopysingletables,Seq("policyno"),"left_outer")
    singgleTable.show()

    singgleTable.repartition(1).write.csv("/root/data/singletable/"+args(0))

    val count=singgleTable.count()
    println(count)
    singgleTable.printSchema()
    spark.stop()
  }
}
