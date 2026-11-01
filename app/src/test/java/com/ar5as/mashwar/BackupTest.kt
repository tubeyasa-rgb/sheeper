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
        val point=Pickup(name="private-location",status="UNSAFE",note="avoid",recorded=1000)
        val encoded=Backup.encode(listOf(row),listOf(point),"correct password")
        assertFalse(String(encoded,Charsets.ISO_8859_1).contains("private-location"))
        val decoded=Backup.decode(encoded,"correct password")
        assertEquals(65.0,decoded.observations.single().actualPaid!!,0.001); assertEquals(true,decoded.observations.single().bookingSuccess)
        assertEquals(point,decoded.pickups.single())
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
        assertEquals(1,decoded.observations.size); assertTrue(decoded.pickups.isEmpty()); assertNull(decoded.observations.single().actualPaid)
    }
}
