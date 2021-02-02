package havis.device.rf.nur;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import havis.device.rf.nur.Constants.Antenna;
import havis.device.rf.nur.Constants.LinkFrequency;
import havis.device.rf.nur.Constants.Region;
import havis.device.rf.nur.Constants.TxLevel;

import org.junit.Test;

import com.nordicid.nurapi.NurApi;

public class ConstantsTest {

	@Test
	public void testRegion() {

		assertEquals(Region.RegionEU, Region.fromRegionCode(Region.RegionEU.regionCode));
		assertEquals(Region.RegionFCC, Region.fromRegionCode(Region.RegionFCC.regionCode));
		assertEquals(Region.RegionJapan, Region.fromRegionCode(Region.RegionJapan.regionCode));
		assertEquals(Region.RegionChina, Region.fromRegionCode(Region.RegionChina.regionCode));
		assertEquals(Region.RegionUnspecified, Region.fromRegionCode(Region.RegionUnspecified.regionCode));

		assertEquals(Region.RegionEU, Region.fromNurApiRegion(NurApi.REGIONID_EU));
		assertEquals(Region.RegionFCC, Region.fromNurApiRegion(NurApi.REGIONID_FCC));
		assertEquals(Region.RegionJapan, Region.fromNurApiRegion(NurApi.REGIONID_JA250MW));
		assertEquals(Region.RegionChina, Region.fromNurApiRegion(NurApi.REGIONID_PRC));

		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_AUSTRALIA));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_BRAZIL));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_CUSTOM));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_INDIA));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_JA500MW));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_KOREA_LBT));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_LAST));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_MALAYSIA));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_NEWZEALAND));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_PHILIPPINES));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_RUSSIA));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_SINGAPORE));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_THAILAND));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_VIETNAM));
		assertEquals(Region.RegionUnspecified, Region.fromNurApiRegion(NurApi.REGIONID_CUSTOM));
	}
	
	@Test
	public void testLinkFrequency() {

		for (short freq : new short[] { 160, 256, 320 })
			assertEquals(LinkFrequency.fromKHz(freq).kHz, freq);

		for (int nurConst : new int[] { NurApi.LINK_FREQUENCY_160000,
				NurApi.LINK_FREQUENCY_256000, NurApi.LINK_FREQUENCY_320000 })
			assertEquals(LinkFrequency.fromNurConst(nurConst).nurApiConstant,
					nurConst);

		try {
			LinkFrequency.fromKHz((short) -999);
			fail();
		} catch (IllegalArgumentException e) {
		}

		try {
			LinkFrequency.fromNurConst((short) 999);
			fail();
		} catch (IllegalArgumentException e) {
		}

	}

	@Test
	public void testAntenna() {

		Antenna ant;
		for (short id = 0; id <= 4; id++) {
			ant = Antenna.fromId(id);
			assertEquals(id, ant.id);
		}

		try {
			ant = Antenna.fromId((short) 999);
			fail();
		} catch (IllegalArgumentException e) {
		}

		for (int id : new int[] { NurApi.ANTENNAID_AUTOSELECT, NurApi.ANTENNAID_1, NurApi.ANTENNAID_2, 
				NurApi.ANTENNAID_3, NurApi.ANTENNAID_4 }) {
			ant = Antenna.fromNurApiAntennaId(id);
			assertEquals(id, ant.nurApiAntId);
		}

		try {
			ant = Antenna.fromNurApiAntennaId((short) 999);
			fail();
		} catch (IllegalArgumentException e) {
		}

		for (int mask : new int[] { NurApi.ANTENNAMASK_ALL, NurApi.ANTENNAMASK_1, NurApi.ANTENNAMASK_2,
				NurApi.ANTENNAMASK_3, NurApi.ANTENNAMASK_4 }) {
			ant = Antenna.fromNurApiAntennaMask(mask);
			assertEquals(mask, ant.nurApiAntMask);
		}

		try {
			ant = Antenna.fromNurApiAntennaMask((short) 999);
			fail();
		} catch (IllegalArgumentException e) {
		}
	}

	@Test
	public void testTxLevel() {
		TxLevel txl = TxLevel.fromDBm((short) 0);
		assertEquals(0, txl.dBm);

		for (short dBm = 8; dBm <= 27; dBm++) {
			txl = TxLevel.fromDBm(dBm);
			assertEquals(dBm, txl.dBm);
		}

		try {
			txl = TxLevel.fromDBm((short) 999);
			fail();
		} catch (IllegalArgumentException e) {
		}

		assertEquals(NurApi.TXLEVEL_8, TxLevel.fromNurConstant(NurApi.TXLEVEL_8).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_9, TxLevel.fromNurConstant(NurApi.TXLEVEL_9).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_10, TxLevel.fromNurConstant(NurApi.TXLEVEL_10).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_11, TxLevel.fromNurConstant(NurApi.TXLEVEL_11).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_12, TxLevel.fromNurConstant(NurApi.TXLEVEL_12).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_13, TxLevel.fromNurConstant(NurApi.TXLEVEL_13).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_14, TxLevel.fromNurConstant(NurApi.TXLEVEL_14).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_15, TxLevel.fromNurConstant(NurApi.TXLEVEL_15).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_16, TxLevel.fromNurConstant(NurApi.TXLEVEL_16).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_17, TxLevel.fromNurConstant(NurApi.TXLEVEL_17).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_18, TxLevel.fromNurConstant(NurApi.TXLEVEL_18).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_19, TxLevel.fromNurConstant(NurApi.TXLEVEL_19).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_20, TxLevel.fromNurConstant(NurApi.TXLEVEL_20).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_21, TxLevel.fromNurConstant(NurApi.TXLEVEL_21).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_22, TxLevel.fromNurConstant(NurApi.TXLEVEL_22).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_23, TxLevel.fromNurConstant(NurApi.TXLEVEL_23).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_24, TxLevel.fromNurConstant(NurApi.TXLEVEL_24).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_25, TxLevel.fromNurConstant(NurApi.TXLEVEL_25).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_26, TxLevel.fromNurConstant(NurApi.TXLEVEL_26).nurApiConstant);
		assertEquals(NurApi.TXLEVEL_27, TxLevel.fromNurConstant(NurApi.TXLEVEL_27).nurApiConstant);
		assertEquals(255 ,TxLevel.fromNurConstant(255).nurApiConstant);

		try {
			txl = TxLevel.fromNurConstant((short) 999);
			fail();
		} catch (IllegalArgumentException e) {
		}

	}
}
