package com.ar5as.mashwar

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageDeviceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun encryptedBackupRoundTripAndWrongPassword() {
        val now=System.currentTimeMillis()
        val row=Observation(origin="A",destination="B",provider="P",category="C",price=50.0,captured=now,actualPaid=55.0,acceptedFare=52.0,bookingSuccess=true)
        val pickup=Pickup(name="X",status="UNSAFE",note="road",recorded=now)
        val bytes=Backup.encode(listOf(row),listOf(pickup),"correct horse")
        assertFalse(String(bytes,Charsets.ISO_8859_1).contains("road"))
        val decoded=Backup.decode(bytes,"correct horse")
        assertEquals(55.0,decoded.observations.single().actualPaid!!,0.001)
        assertEquals(true,decoded.observations.single().bookingSuccess)
        assertEquals("UNSAFE",decoded.pickups.single().status)
        try { Backup.decode(bytes,"wrong password"); fail("wrong password accepted") } catch (_:javax.crypto.AEADBadTagException) { }
    }
    @Test fun migrationPreservesExistingQuotesAndAcceptsFeedback()=runBlocking {
        val name="migration-device-test.db"; context.deleteDatabase(name)
        val cfg=SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE observations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, origin TEXT NOT NULL, destination TEXT NOT NULL, provider TEXT NOT NULL, category TEXT NOT NULL, price REAL NOT NULL, captured INTEGER NOT NULL, provenance TEXT NOT NULL)")
                db.execSQL("CREATE INDEX index_observations_origin_destination_category_provider ON observations (origin, destination, category, provider)")
                db.execSQL("INSERT INTO observations (origin,destination,provider,category,price,captured,provenance) VALUES ('A','B','P','C',50,1000,'USER_ENTERED')")
            }
            override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) {}
        }).build()
        FrameworkSQLiteOpenHelperFactory().create(cfg).use { it.writableDatabase }
        val db=Room.databaseBuilder(context,RideDb::class.java,name).addMigrations(RideDb.MIGRATION_1_2,RideDb.MIGRATION_2_3,RideDb.MIGRATION_3_4,RideDb.MIGRATION_4_5).build()
        try {
            val row=db.observations().all().single(); assertEquals(50.0,row.price,0.001); assertNull(row.actualPaid)
            db.observations().setFeedback(row.id,60.0,55.0,true)
            assertEquals(60.0,db.observations().all().single().actualPaid!!,0.001)
            db.observations().insertPickup(Pickup(name="X",status="UNKNOWN",note="",recorded=1000))
            assertEquals(1,db.observations().allPickups().size)
            db.observations().importBackup(BackupContents(listOf(row.copy(actualPaid=80.0)),listOf(Pickup(name="X",status="UNKNOWN",note="",recorded=1000))))
            assertEquals(1,db.observations().all().size)
            assertEquals(60.0,db.observations().all().single().actualPaid!!,0.001)
            assertEquals(1,db.observations().allPickups().size)
            val started=System.currentTimeMillis()-600_000
            assertTrue(db.observations().startMeasuredTrip(row,started)); assertFalse(db.observations().startMeasuredTrip(row,started))
            val active=db.observations().allTraffic().single(); assertEquals("ACTIVE",active.kind)
            assertEquals(1,db.observations().finishTraffic(active.id,started,10.0,started+600_000))
            assertEquals("MEASURED",db.observations().allTraffic().single().kind)
            db.observations().deleteWithOrphanTraffic(row); assertTrue(db.observations().allTraffic().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
