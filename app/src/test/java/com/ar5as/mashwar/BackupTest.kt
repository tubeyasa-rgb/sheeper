package com.ar5as.mashwar

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupTest {
    @Test fun roundTripProtectsAndRestoresFeedbackAndPickup() {
        val row=Observation(origin="home",destination="work",provider="provider",category="economy",price=50.0,captured=1000,actualPaid=65.0,acceptedFare=60.0,bookingSuccess=true)
        val point=Pickup(name="private-location",status="UNSAFE",note="avoid",recorded=1000,walkMeters=130.0,walkMinutes=3.0,direct=true)
        val traffic=TrafficSample(origin="home",destination="work",provider="provider",category="economy",kind="MEASURED",minutes=10.0,startedAt=1000,recordedAt=601000,distanceKm=12.5,inputSource="CONFIRMED_IMAGE")
        val active=traffic.copy(kind="ACTIVE",minutes=null,startedAt=1000,recordedAt=1000)
        val encoded=Backup.encode(listOf(row),listOf(point),"correct password",listOf(traffic,active))
        assertFalse(String(encoded,Charsets.ISO_8859_1).contains("private-location"))
        val decoded=Backup.decode(encoded,"correct password")
        assertEquals(65.0,decoded.observations.single().actualPaid!!,0.001); assertEquals(true,decoded.observations.single().bookingSuccess)
        assertEquals(point,decoded.pickups.single()); assertEquals(traffic,decoded.traffic.single())
        try { Backup.decode(encoded,"wrong password"); fail("accepted wrong password") } catch (_:AEADBadTagException) { }
        encoded[encoded.lastIndex]=(encoded.last().toInt() xor 1).toByte()
        try { Backup.decode(encoded,"correct password"); fail("accepted tampered file") } catch (_:AEADBadTagException) { }
    }
    @Test fun importsOriginalBackupFormat() {
        val salt=ByteArray(16); val iv=ByteArray(12)
        val spec=PBEKeySpec("old password".toCharArray(),salt,210000,256)
        val key=SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,iv))
        val original="""[{"origin":"A","destination":"B","provider":"C","category":"D","price":50,"captured":1000}]""".toByteArray()
        val backup="SHP1".toByteArray()+salt+iv+cipher.doFinal(original)
        val decoded=Backup.decode(backup,"old password")
        assertEquals(1,decoded.observations.size); assertTrue(decoded.pickups.isEmpty()); assertTrue(decoded.traffic.isEmpty()); assertNull(decoded.observations.single().actualPaid)
    }
    @Test fun importsVersionThreeBackupWithoutTraffic() {
        val salt=ByteArray(16); val iv=ByteArray(12)
        val spec=PBEKeySpec("version three".toCharArray(),salt,210000,256)
        val key=SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,iv))
        val original="""{"version":3,"observations":[{"origin":"A","destination":"B","provider":"C","category":"D","price":50,"captured":1000}],"pickups":[{"name":"corner","status":"UNKNOWN","note":"","recorded":1000,"walkMeters":200,"walkMinutes":4,"direct":true}]}""".toByteArray()
        val decoded=Backup.decode("SHP1".toByteArray()+salt+iv+cipher.doFinal(original),"version three")
        assertEquals(1,decoded.observations.size); assertEquals(200.0,decoded.pickups.single().walkMeters!!,0.001); assertTrue(decoded.traffic.isEmpty())
    }
    @Test fun importsVersionFourTrafficWithoutDistance() {
        val salt=ByteArray(16); val iv=ByteArray(12); val spec=PBEKeySpec("version four".toCharArray(),salt,210000,256)
        val key=SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,iv))
        val original="""{"version":4,"observations":[],"pickups":[],"traffic":[{"origin":"A","destination":"B","provider":"C","category":"D","kind":"USER_ESTIMATE","minutes":20,"startedAt":null,"recordedAt":1000}]}""".toByteArray()
        val decoded=Backup.decode("SHP1".toByteArray()+salt+iv+cipher.doFinal(original),"version four")
        assertEquals(20.0,decoded.traffic.single().minutes!!,0.001); assertNull(decoded.traffic.single().distanceKm); assertNull(decoded.traffic.single().inputSource)
    }
}
