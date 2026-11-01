package com.ar5as.mashwar

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class MigrationTest {
    @Test fun migrateV2PickupTableWithoutLosingSavedPoints() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { sql ->
            sql.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE pickups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, note TEXT NOT NULL, recorded INTEGER NOT NULL)")
                stmt.execute("INSERT INTO pickups (name,status,note,recorded) VALUES ('corner','UNKNOWN','near street',1000)")
            }
            val db=Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java)) { _,method,args ->
                if(method.name=="execSQL" && args?.size==1) { sql.createStatement().use { it.execute(args[0] as String) }; null }
                else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            RideDb.MIGRATION_2_3.migrate(db)
            sql.createStatement().use { stmt -> stmt.executeQuery("SELECT name,status,note,recorded,walkMeters,walkMinutes,direct FROM pickups").use { rows ->
                assertTrue(rows.next()); assertEquals("corner",rows.getString(1)); assertEquals("UNKNOWN",rows.getString(2)); assertEquals("near street",rows.getString(3)); assertEquals(1000,rows.getInt(4)); assertNull(rows.getObject(5)); assertNull(rows.getObject(6)); assertNull(rows.getObject(7)); assertFalse(rows.next())
            } }
        }
    }
    @Test fun migrateV3AddsTrafficWithoutChangingExistingTrips() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { sql ->
            sql.createStatement().use { it.execute("CREATE TABLE observations (id INTEGER PRIMARY KEY, origin TEXT)"); it.execute("INSERT INTO observations VALUES (1,'home')") }
            val db=Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java)) { _,method,args ->
                if(method.name=="execSQL" && args?.size==1) { sql.createStatement().use { it.execute(args[0] as String) }; null } else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            RideDb.MIGRATION_3_4.migrate(db)
            sql.createStatement().use { statement ->
                statement.executeQuery("SELECT origin FROM observations WHERE id=1").use { assertTrue(it.next()); assertEquals("home",it.getString(1)) }
                statement.execute("INSERT INTO traffic_samples (origin,destination,provider,category,kind,minutes,startedAt,recordedAt) VALUES ('home','work','app','class','MEASURED',10,1000,601000)")
                statement.executeQuery("SELECT minutes FROM traffic_samples").use { assertTrue(it.next()); assertEquals(10.0,it.getDouble(1),0.001) }
            }
        }
    }
    @Test fun migrateV4KeepsMeasuredTimesAndAddsRouteFields() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { sql ->
            sql.createStatement().use { it.execute("CREATE TABLE traffic_samples (id INTEGER PRIMARY KEY, minutes REAL, recordedAt INTEGER)"); it.execute("INSERT INTO traffic_samples VALUES (1,25,1000)") }
            val db=Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,arrayOf(SupportSQLiteDatabase::class.java)) { _,method,args ->
                if(method.name=="execSQL" && args?.size==1) { sql.createStatement().use { it.execute(args[0] as String) }; null } else throw UnsupportedOperationException(method.name)
            } as SupportSQLiteDatabase
            RideDb.MIGRATION_4_5.migrate(db)
            sql.createStatement().use { statement -> statement.executeQuery("SELECT minutes,distanceKm,inputSource FROM traffic_samples WHERE id=1").use { rows -> assertTrue(rows.next()); assertEquals(25.0,rows.getDouble(1),0.001); assertNull(rows.getObject(2)); assertNull(rows.getObject(3)) } }
        }
    }
}
