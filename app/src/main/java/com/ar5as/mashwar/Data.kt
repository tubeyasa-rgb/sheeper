package com.ar5as.mashwar

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Entity(tableName="observations", indices=[Index(value=["origin","destination","category","provider"])])
data class Observation(@PrimaryKey(autoGenerate=true) val id: Long=0, val origin:String, val destination:String, val provider:String, val category:String, val price:Double, val captured:Long, val provenance:String="USER_ENTERED", val actualPaid:Double?=null, val acceptedFare:Double?=null, val bookingSuccess:Boolean?=null)
@Entity(tableName="pickups")
data class Pickup(@PrimaryKey(autoGenerate=true) val id:Long=0, val name:String, val status:String, val note:String, val recorded:Long, val walkMeters:Double?=null, val walkMinutes:Double?=null, val direct:Boolean?=null)
@Entity(tableName="traffic_samples", indices=[Index(value=["origin","destination","provider","category"])])
data class TrafficSample(@PrimaryKey(autoGenerate=true) val id:Long=0, val origin:String, val destination:String, val provider:String, val category:String, val kind:String, val minutes:Double?=null, val startedAt:Long?=null, val recordedAt:Long, val distanceKm:Double?=null, val inputSource:String?=null)
data class BackupContents(val observations:List<Observation>, val pickups:List<Pickup>, val traffic:List<TrafficSample> = emptyList())
@Dao interface ObservationDao {
    @Query("SELECT * FROM observations ORDER BY captured DESC") fun observe(): Flow<List<Observation>>
    @Query("SELECT * FROM observations ORDER BY captured DESC") suspend fun all(): List<Observation>
    @Insert suspend fun insert(item:Observation):Long
    @Query("UPDATE observations SET actualPaid=COALESCE(actualPaid,:paid), acceptedFare=COALESCE(acceptedFare,:accepted), bookingSuccess=COALESCE(bookingSuccess,:success) WHERE id=:id") suspend fun mergeFeedback(id:Long,paid:Double?,accepted:Double?,success:Boolean?)
    @Query("UPDATE observations SET actualPaid=:paid, acceptedFare=:accepted, bookingSuccess=:success WHERE id=:id") suspend fun setFeedback(id:Long,paid:Double?,accepted:Double?,success:Boolean?)
    @Query("DELETE FROM observations WHERE id=:id") suspend fun delete(id:Long)
    @Query("DELETE FROM observations") suspend fun clear()
    @Query("SELECT * FROM pickups ORDER BY recorded DESC") fun observePickups(): Flow<List<Pickup>>
    @Query("SELECT * FROM pickups ORDER BY recorded DESC") suspend fun allPickups(): List<Pickup>
    @Insert suspend fun insertPickup(item:Pickup):Long
    @Query("DELETE FROM pickups WHERE id=:id") suspend fun deletePickup(id:Long)
    @Query("UPDATE pickups SET walkMeters=COALESCE(walkMeters,:meters), walkMinutes=COALESCE(walkMinutes,:minutes), direct=COALESCE(direct,:direct) WHERE id=:id") suspend fun mergePickup(id:Long,meters:Double?,minutes:Double?,direct:Boolean?)
    @Query("DELETE FROM pickups") suspend fun clearPickups()
    @Query("SELECT * FROM traffic_samples ORDER BY recordedAt DESC") fun observeTraffic():Flow<List<TrafficSample>>
    @Query("SELECT * FROM traffic_samples ORDER BY recordedAt DESC") suspend fun allTraffic():List<TrafficSample>
    @Insert suspend fun insertTraffic(sample:TrafficSample):Long
    @Transaction suspend fun startMeasuredTrip(reference:Observation,now:Long):Boolean {
        if(allTraffic().any { it.kind=="ACTIVE" && TrafficEngine.sameRoute(it,reference) }) return false
        insertTraffic(TrafficSample(origin=reference.origin,destination=reference.destination,provider=reference.provider,category=reference.category,kind="ACTIVE",startedAt=now,recordedAt=now))
        return true
    }
    @Query("UPDATE traffic_samples SET kind='MEASURED',minutes=:minutes,recordedAt=:endedAt WHERE id=:id AND kind='ACTIVE' AND startedAt=:startedAt") suspend fun finishTraffic(id:Long,startedAt:Long,minutes:Double,endedAt:Long):Int
    @Query("DELETE FROM traffic_samples WHERE id=:id") suspend fun deleteTraffic(id:Long)
    @Query("UPDATE traffic_samples SET distanceKm=COALESCE(distanceKm,:distance), inputSource=COALESCE(inputSource,:source) WHERE id=:id") suspend fun mergeTraffic(id:Long,distance:Double?,source:String?)
    @Query("DELETE FROM traffic_samples") suspend fun clearTraffic()
    @Transaction suspend fun deleteWithOrphanTraffic(item:Observation) {
        delete(item.id)
        if(all().none { Logic.comparable(it,item) && Logic.same(it.provider,item.provider) }) allTraffic().filter { TrafficEngine.sameRoute(it,item) }.forEach { deleteTraffic(it.id) }
    }
    @Transaction suspend fun clearAll() { clear(); clearPickups(); clearTraffic() }
    @Transaction suspend fun importItems(items:List<Observation>) { val existing=all().toMutableList(); items.forEach { val old=existing.find { previous -> Logic.duplicate(previous,it) }; if(old == null) { val id=insert(it.copy(id=0)); existing.add(it.copy(id=id)) } else { mergeFeedback(old.id,it.actualPaid,it.acceptedFare,it.bookingSuccess) } } }
    @Transaction suspend fun importBackup(backup:BackupContents) {
        importItems(backup.observations)
        val existing=allPickups().toMutableList()
        backup.pickups.forEach { item -> val old=existing.find { it.name==item.name && it.status==item.status && it.note==item.note && it.recorded==item.recorded }; if(old==null) { val id=insertPickup(item.copy(id=0)); existing.add(item.copy(id=id)) } else mergePickup(old.id,item.walkMeters,item.walkMinutes,item.direct) }
        val prior=allTraffic().toMutableList()
        backup.traffic.forEach { item -> val old=prior.find { it.kind==item.kind && it.recordedAt==item.recordedAt && it.minutes==item.minutes && it.startedAt==item.startedAt && it.origin==item.origin && it.destination==item.destination && it.provider==item.provider && it.category==item.category }; if(old==null) { val id=insertTraffic(item.copy(id=0)); prior.add(item.copy(id=id)) } else mergeTraffic(old.id,item.distanceKm,item.inputSource) }
    }
}
@Database(entities=[Observation::class,Pickup::class,TrafficSample::class],version=5,exportSchema=false)
abstract class RideDb:RoomDatabase() {
    abstract fun observations():ObservationDao
    companion object {
        val MIGRATION_1_2=object:Migration(1,2) { override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE observations ADD COLUMN actualPaid REAL")
            db.execSQL("ALTER TABLE observations ADD COLUMN acceptedFare REAL")
            db.execSQL("ALTER TABLE observations ADD COLUMN bookingSuccess INTEGER")
            db.execSQL("CREATE TABLE IF NOT EXISTS pickups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, note TEXT NOT NULL, recorded INTEGER NOT NULL)")
        } }
        val MIGRATION_2_3=object:Migration(2,3) { override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pickups ADD COLUMN walkMeters REAL")
            db.execSQL("ALTER TABLE pickups ADD COLUMN walkMinutes REAL")
            db.execSQL("ALTER TABLE pickups ADD COLUMN direct INTEGER")
        } }
        val MIGRATION_3_4=object:Migration(3,4) { override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS traffic_samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, origin TEXT NOT NULL, destination TEXT NOT NULL, provider TEXT NOT NULL, category TEXT NOT NULL, kind TEXT NOT NULL, minutes REAL, startedAt INTEGER, recordedAt INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_traffic_samples_origin_destination_provider_category ON traffic_samples (origin, destination, provider, category)")
        } }
        val MIGRATION_4_5=object:Migration(4,5) { override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE traffic_samples ADD COLUMN distanceKm REAL")
            db.execSQL("ALTER TABLE traffic_samples ADD COLUMN inputSource TEXT")
        } }
        fun open(context:Context):RideDb=Room.databaseBuilder(context,RideDb::class.java,"observations-room.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5).build()
    }
}
object Logic {
    const val FRESH=180_000L
    fun same(a:String,b:String)=a.trim().equals(b.trim(),ignoreCase=true)
    fun comparable(a:Observation,b:Observation)=same(a.origin,b.origin)&&same(a.destination,b.destination)&&same(a.category,b.category)
    fun duplicate(a:Observation,b:Observation)=comparable(a,b)&&same(a.provider,b.provider)&&a.price==b.price&&kotlin.math.abs(a.captured-b.captured)<=90_000
    fun fresh(o:Observation,now:Long)=now>=o.captured&&now-o.captured<=FRESH
    fun lowest(items:List<Observation>,baseline:Observation,now:Long):Observation?=items.filter{comparable(it,baseline)&&fresh(it,now)}.minByOrNull{it.price}
    fun parseNonNegative(s:String):Double? {
        val text=buildString { for(c in s.trim()) { val digit=Character.digit(c,10); when { digit>=0 -> append(digit); c=='.'||c=='٫' -> append('.'); c==','||c=='٬'||c.isWhitespace() -> {}; else -> return null } } }
        return text.toDoubleOrNull()?.takeIf{it.isFinite()&&it>=0&&it<=1_000_000}
    }
    fun parsePrice(s:String):Double? = parseNonNegative(s)?.takeIf { it>0 }
    fun forecastMessage(items:List<Observation>):String="لا يوجد توقع معاير بعد (${items.size} عرض مسجل). يلزم سجل كافٍ واختبار زمني للمعايرة قبل عرض سعر متوقع."
}
object Backup {
    private val random=SecureRandom()
    fun encode(items:List<Observation>,pickups:List<Pickup>,password:String,traffic:List<TrafficSample> = emptyList()):ByteArray {
        val rows=JSONArray(); items.forEach { rows.put(JSONObject().put("origin",it.origin).put("destination",it.destination).put("provider",it.provider).put("category",it.category).put("price",it.price).put("captured",it.captured).put("provenance",it.provenance).put("actualPaid",it.actualPaid?:JSONObject.NULL).put("acceptedFare",it.acceptedFare?:JSONObject.NULL).put("bookingSuccess",it.bookingSuccess?:JSONObject.NULL)) }
        val points=JSONArray(); pickups.forEach { points.put(JSONObject().put("name",it.name).put("status",it.status).put("note",it.note).put("recorded",it.recorded).put("walkMeters",it.walkMeters?:JSONObject.NULL).put("walkMinutes",it.walkMinutes?:JSONObject.NULL).put("direct",it.direct?:JSONObject.NULL)) }
        val trips=JSONArray(); traffic.filter { it.kind != "ACTIVE" }.forEach { trips.put(JSONObject().put("origin",it.origin).put("destination",it.destination).put("provider",it.provider).put("category",it.category).put("kind",it.kind).put("minutes",it.minutes?:JSONObject.NULL).put("startedAt",it.startedAt?:JSONObject.NULL).put("recordedAt",it.recordedAt).put("distanceKm",it.distanceKm?:JSONObject.NULL).put("inputSource",it.inputSource?:JSONObject.NULL)) }
        val json=JSONObject().put("version",5).put("observations",rows).put("pickups",points).put("traffic",trips)
        val salt=ByteArray(16).also(random::nextBytes); val iv=ByteArray(12).also(random::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),GCMParameterSpec(128,iv))
        return "SHP1".toByteArray()+salt+iv+cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
    }
    fun decode(bytes:ByteArray,password:String):BackupContents {
        require(bytes.size in 48..5_000_000&&bytes.copyOfRange(0,4).contentEquals("SHP1".toByteArray()))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(password,bytes.copyOfRange(4,20)),GCMParameterSpec(128,bytes.copyOfRange(20,32)))
        val raw=String(cipher.doFinal(bytes.copyOfRange(32,bytes.size)),Charsets.UTF_8).trim()
        val root=if(raw.startsWith("[")) null else JSONObject(raw).also { require(it.getInt("version") in 2..5) }
        val arr=root?.getJSONArray("observations")?:JSONArray(raw); val points=root?.getJSONArray("pickups")?:JSONArray(); val trips=root?.optJSONArray("traffic")?:JSONArray(); require(arr.length()<=10000&&points.length()<=10000&&trips.length()<=10000)
        fun optionalPrice(o:JSONObject,k:String):Double? = if(!o.has(k)||o.isNull(k)) null else o.getDouble(k).also { require(it.isFinite()&&it>0&&it<=1_000_000) }
        val observations=(0 until arr.length()).map { val x=arr.getJSONObject(it); val price=x.getDouble("price"); val timestamp=x.getLong("captured"); val a=x.getString("origin"); val b=x.getString("destination"); val p=x.getString("provider"); val c=x.getString("category"); require(listOf(a,b,p,c).all{it.isNotBlank()&&it.length<=200}&&price.isFinite()&&price>0&&price<=1_000_000&&timestamp>0&&timestamp<=System.currentTimeMillis()); Observation(origin=a,destination=b,provider=p,category=c,price=price,captured=timestamp,provenance="IMPORTED",actualPaid=optionalPrice(x,"actualPaid"),acceptedFare=optionalPrice(x,"acceptedFare"),bookingSuccess=if(!x.has("bookingSuccess")||x.isNull("bookingSuccess")) null else x.getBoolean("bookingSuccess")) }
        val pickups=(0 until points.length()).map { val x=points.getJSONObject(it); val name=x.getString("name"); val status=x.getString("status"); val note=x.getString("note"); val time=x.getLong("recorded"); require(name.isNotBlank()&&name.length<=200&&note.length<=500&&status in listOf("UNKNOWN","UNSAFE")&&time>0&&time<=System.currentTimeMillis()); val meters=optionalPrice(x,"walkMeters"); val minutes=optionalPrice(x,"walkMinutes"); require((meters==null||meters<=10000)&&(minutes==null||minutes<=180)); Pickup(name=name,status=status,note=note,recorded=time,walkMeters=meters,walkMinutes=minutes,direct=if(!x.has("direct")||x.isNull("direct")) null else x.getBoolean("direct")) }
        val traffic=(0 until trips.length()).map { val x=trips.getJSONObject(it); val a=x.getString("origin"); val b=x.getString("destination"); val p=x.getString("provider"); val c=x.getString("category"); val kind=x.getString("kind"); val minutes=x.getDouble("minutes"); val recorded=x.getLong("recordedAt"); val start=if(!x.has("startedAt")||x.isNull("startedAt")) null else x.getLong("startedAt"); require(listOf(a,b,p,c).all { it.isNotBlank()&&it.length<=200 } && kind in listOf("MEASURED","USER_ESTIMATE") && minutes.isFinite() && minutes>0 && minutes<=1440 && recorded>0 && recorded<=System.currentTimeMillis() && (start==null || start>0 && start<=recorded) && (kind!="MEASURED" || start!=null)); val distance=optionalPrice(x,"distanceKm"); require(distance==null || distance<=2000); val source=if(!x.has("inputSource")||x.isNull("inputSource")) null else x.getString("inputSource"); require(source==null || source in listOf("MANUAL","CONFIRMED_IMAGE")); TrafficSample(origin=a,destination=b,provider=p,category=c,kind=kind,minutes=minutes,startedAt=start,recordedAt=recorded,distanceKm=distance,inputSource=source) }
        return BackupContents(observations,pickups,traffic)
    }
    private fun key(password:String,salt:ByteArray):SecretKeySpec { val spec=PBEKeySpec(password.toCharArray(),salt,210000,256); return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") } finally { spec.clearPassword() } }
}
