package havis.device.rf.nur;

import com.nordicid.nurapi.NurApi;

public abstract class Constants {

	/**
	 * This enum maps link frequencies to the corresponding NUR constants.
	 * 
	 */
	protected enum LinkFrequency {
		/**
		 * Constant for link frequency 160 kHz (NurApi.LINK_FREQUENCY_160000)
		 */
		Frequency160kHz((short) 160, NurApi.LINK_FREQUENCY_160000),
		/**
		 * Constant for link frequency 256 kHz (NurApi.LINK_FREQUENCY_256000)
		 */
		Frequency256kHz((short) 256, NurApi.LINK_FREQUENCY_256000),
		/**
		 * Constant for link frequency 320 kHz (NurApi.LINK_FREQUENCY_320000)
		 */
		Frequency320kHz((short) 320, NurApi.LINK_FREQUENCY_320000);

		final short kHz;
		final int nurApiConstant;

		private LinkFrequency(short kHz, int nurApiConstant) {
			this.kHz = kHz;
			this.nurApiConstant = nurApiConstant;
		}

		/**
		 * Returns a {@link LinkFrequency} instance for a specific frequency in
		 * kHz.
		 * 
		 * @param kHz
		 *            a specific frequency in kHz
		 * @return a {@link LinkFrequency} instance
		 */
		static LinkFrequency fromKHz(short kHz) {
			switch (kHz) {
			case 160:
				return Frequency160kHz;
			case 256:
				return Frequency256kHz;
			case 320:
				return Frequency320kHz;
			default:
				throw new IllegalArgumentException(
						"No enum constant found for frequency: " + kHz);
			}
		}

		/**
		 * Returns a {@link LinkFrequency} instance for a specific Nur API
		 * constant.
		 * 
		 * @param constVal
		 *            a specific Nur API constant.
		 * @return a {@link LinkFrequency} instance
		 */
		static LinkFrequency fromNurConst(int constVal) {
			switch (constVal) {
			case NurApi.LINK_FREQUENCY_160000:
				return Frequency160kHz;
			case NurApi.LINK_FREQUENCY_256000:
				return Frequency256kHz;
			case NurApi.LINK_FREQUENCY_320000:
				return Frequency320kHz;
			default:
				throw new IllegalArgumentException(
						"No enum constant found for value: " + constVal);
			}
		}
	}

	/**
	 * This enum maps antenna IDs to Nur API constants and antenna masks.
	 * 
	 */
	protected enum Antenna {

		/**
		 * Antenna ID for automatic mode (0) mapped to
		 * NurApi.ANTENNAID_AUTOSELECT and NurApi.ANTENNAMASK_ALL
		 */
		Auto((short) 0, NurApi.ANTENNAID_AUTOSELECT, NurApi.ANTENNAMASK_ALL),
		/**
		 * Antenna ID 1 mapped to NurApi.ANTENNAID_1 and NurApi.ANTENNAMASK_1
		 */
		Antenna1((short) 1, NurApi.ANTENNAID_1, NurApi.ANTENNAMASK_1),
		/**
		 * Antenna ID 2 mapped to NurApi.ANTENNAID_2 and NurApi.ANTENNAMASK_2
		 */
		Antenna2((short) 2, NurApi.ANTENNAID_2, NurApi.ANTENNAMASK_2),
		/**
		 * Antenna ID 3 mapped to NurApi.ANTENNAID_3 and NurApi.ANTENNAMASK_3
		 */
		Antenna3((short) 3, NurApi.ANTENNAID_3, NurApi.ANTENNAMASK_3),
		/**
		 * Antenna ID 4 mapped to NurApi.ANTENNAID_4 and NurApi.ANTENNAMASK_4
		 */
		Antenna4((short) 4, NurApi.ANTENNAID_4, NurApi.ANTENNAMASK_4);

		final short id;
		final int nurApiAntId;
		final int nurApiAntMask;

		private Antenna(short id, int nurApiAntId, int nurApiAntMask) {
			this.id = id;
			this.nurApiAntId = nurApiAntId;
			this.nurApiAntMask = nurApiAntMask;
		}

		/**
		 * Returns an {@link Antenna} instance for a specific antenna ID
		 * 
		 * @param id
		 *            an antenna ID
		 * @return an {@link Antenna} instance
		 */
		static Antenna fromId(short id) {
			switch (id) {
			case 0:
				return Auto;
			case 1:
				return Antenna1;
			case 2:
				return Antenna2;
			case 3:
				return Antenna3;
			case 4:
				return Antenna4;
			default:
				throw new IllegalArgumentException(
						"No enum constant found for antenna with ID: " + id);
			}
		}

		/**
		 * Returns an {@link Antenna} instance for a specific Nur API constant
		 * 
		 * @param id
		 *            a Nur API constant (e.g. NurApi.ANTENNAID_AUTOSELECT,
		 *            NurApi.ANTENNAID_1)
		 * @return an {@link Antenna} instance
		 */
		static Antenna fromNurApiAntennaId(int id) {
			switch (id) {
			case NurApi.ANTENNAID_AUTOSELECT:
				return Auto;
			case NurApi.ANTENNAID_1:
				return Antenna1;
			case NurApi.ANTENNAID_2:
				return Antenna2;
			case NurApi.ANTENNAID_3:
				return Antenna3;
			case NurApi.ANTENNAID_4:
				return Antenna4;
			default:
				throw new IllegalArgumentException(
						"No enum constant found for antenna with ID: " + id);
			}
		}

		/**
		 * Returns an {@link Antenna} instance for a specific antenna mask.
		 * 
		 * @param mask
		 *            A Nur API antenna mask (e.g. NurApi.ANTENNAMASK_1,
		 *            NurApi.ANTENNAMASK_ALL)
		 * @return an {@link Antenna} instance
		 */
		static Antenna fromNurApiAntennaMask(int mask) {
			switch (mask) {
			case NurApi.ANTENNAMASK_ALL:
				return Auto;
			case NurApi.ANTENNAMASK_1:
				return Antenna1;
			case NurApi.ANTENNAMASK_2:
				return Antenna2;
			case NurApi.ANTENNAMASK_3:
				return Antenna3;
			case NurApi.ANTENNAMASK_4:
				return Antenna4;
			default:
				throw new IllegalArgumentException(
						"No enum constant found for antenna with mask: " + mask);
			}
		}
	}

	/**
	 * This enum maps TX levels in dBm and mW to Nur API constants and the
	 * corresponding numeric values.
	 * 
	 */
	protected enum TxLevel {

		/**
		 * Entry for TXLevel 8 dBm = 6 mW = NurApi.TXLEVEL_8
		 */
		TxLevel8((short) 8, (short) 6, NurApi.TXLEVEL_8),

		/**
		 * Entry for TXLevel 9 dBm = 8 mW = NurApi.TXLEVEL_9
		 */
		TxLevel9((short) 9, (short) 8, NurApi.TXLEVEL_9),

		/**
		 * Entry for TXLevel 10 dBm = 10 mW = NurApi.TXLEVEL_10
		 */
		TxLevel10((short) 10, (short) 10, NurApi.TXLEVEL_10),

		/**
		 * Entry for TXLevel 11 dBm = 13 mW = NurApi.TXLEVEL_11
		 */
		TxLevel11((short) 11, (short) 13, NurApi.TXLEVEL_11),

		/**
		 * Entry for TXLevel 12 dBm = 16 mW = NurApi.TXLEVEL_12
		 */
		TxLevel12((short) 12, (short) 16, NurApi.TXLEVEL_12),

		/**
		 * Entry for TXLevel 13 dBm = 20 mW = NurApi.TXLEVEL_13
		 */
		TxLevel13((short) 13, (short) 20, NurApi.TXLEVEL_13),

		/**
		 * Entry for TXLevel 14 dBm = 25 mW = NurApi.TXLEVEL_14
		 */
		TxLevel14((short) 14, (short) 25, NurApi.TXLEVEL_14),

		/**
		 * Entry for TXLevel 15 dBm = 32 mW = NurApi.TXLEVEL_15
		 */
		TxLevel15((short) 15, (short) 32, NurApi.TXLEVEL_15),

		/**
		 * Entry for TXLevel 16 dBm = 40 mW = NurApi.TXLEVEL_16
		 */
		TxLevel16((short) 16, (short) 40, NurApi.TXLEVEL_16),

		/**
		 * Entry for TXLevel 17 dBm = 50 mW = NurApi.TXLEVEL_17
		 */
		TxLevel17((short) 17, (short) 50, NurApi.TXLEVEL_17),

		/**
		 * Entry for TXLevel 18 dBm = 63 mW = NurApi.TXLEVEL_18
		 */
		TxLevel18((short) 18, (short) 63, NurApi.TXLEVEL_18),

		/**
		 * Entry for TXLevel 19 dBm = 79 mW = NurApi.TXLEVEL_19
		 */
		TxLevel19((short) 19, (short) 79, NurApi.TXLEVEL_19),

		/**
		 * Entry for TXLevel 20 dBm = 100 mW = NurApi.TXLEVEL_20
		 */
		TxLevel20((short) 20, (short) 100, NurApi.TXLEVEL_20),

		/**
		 * Entry for TXLevel 21 dBm = 126 mW = NurApi.TXLEVEL_21
		 */
		TxLevel21((short) 21, (short) 126, NurApi.TXLEVEL_21),

		/**
		 * Entry for TXLevel 22 dBm = 158 mW = NurApi.TXLEVEL_22
		 */
		TxLevel22((short) 22, (short) 158, NurApi.TXLEVEL_22),

		/**
		 * Entry for TXLevel 23 dBm = 200 mW = NurApi.TXLEVEL_23
		 */
		TxLevel23((short) 23, (short) 200, NurApi.TXLEVEL_23),

		/**
		 * Entry for TXLevel 24 dBm = 251 mW = NurApi.TXLEVEL_24
		 */
		TxLevel24((short) 24, (short) 251, NurApi.TXLEVEL_24),

		/**
		 * Entry for TXLevel 25 dBm = 316 mW = NurApi.TXLEVEL_25
		 */
		TxLevel25((short) 25, (short) 316, NurApi.TXLEVEL_25),

		/**
		 * Entry for TXLevel 26 dBm = 398 mW = NurApi.TXLEVEL_26
		 */
		TxLevel26((short) 26, (short) 398, NurApi.TXLEVEL_26),

		/**
		 * Entry for TXLevel 27 dBm = 500 mW = NurApi.TXLEVEL_27
		 */
		TxLevel27((short) 27, (short) 500, NurApi.TXLEVEL_27),

		/**
		 * Entry for TXLevel 0 dBm = 0 mW = 255 (Pseudo value for default)
		 */
		TxLevelDefault((short) 0, (short) 0, 255);

		final short dBm;
		final int mW;
		final int nurApiConstant;

		private TxLevel(short dBm, short mW, int nurApiConstant) {
			this.mW = mW;
			this.dBm = dBm;
			this.nurApiConstant = nurApiConstant;
		}

		/**
		 * Returns a {@link TxLevel} instance for a specific dBm value.
		 * 
		 * @param dbm
		 *            a dBm value, valid values range from 8 to 27 dBm (and 0
		 *            for pseudo value TxLevelDefault)
		 * @return a {@link TxLevel} instance
		 * @throws IllegalArgumentException
		 *             if the dBm value passed is not within the valid range.
		 */
		static TxLevel fromDBm(short dbm) {
			switch (dbm) {
			case 0:
				return TxLevelDefault;
			case 8:
				return TxLevel8;
			case 9:
				return TxLevel9;
			case 10:
				return TxLevel10;
			case 11:
				return TxLevel11;
			case 12:
				return TxLevel12;
			case 13:
				return TxLevel13;
			case 14:
				return TxLevel14;
			case 15:
				return TxLevel15;
			case 16:
				return TxLevel16;
			case 17:
				return TxLevel17;
			case 18:
				return TxLevel18;
			case 19:
				return TxLevel19;
			case 20:
				return TxLevel20;
			case 21:
				return TxLevel21;
			case 22:
				return TxLevel22;
			case 23:
				return TxLevel23;
			case 24:
				return TxLevel24;
			case 25:
				return TxLevel25;
			case 26:
				return TxLevel26;
			case 27:
				return TxLevel27;

			default:
				throw new IllegalArgumentException(
						"No enum constant found for TX level (dBm): " + dbm);
			}
		}

		/**
		 * Returns a {@link TxLevel} instance for a specific Nur API constant.
		 * 
		 * @param constVal
		 *            a Nur API constant. Valid values range from
		 *            NurApi.TXLEVEL_8 to NurApi.TXLEVEL_27 (and 255 for pseudo
		 *            value TxLevelDefault)
		 * @return a {@link TxLevel} instance
		 * @throws IllegalArgumentException
		 *             if the constant passed is not within the valid range.
		 */
		static TxLevel fromNurConstant(int constVal) {
			switch (constVal) {
			case NurApi.TXLEVEL_8:
				return TxLevel8;
			case NurApi.TXLEVEL_9:
				return TxLevel9;
			case NurApi.TXLEVEL_10:
				return TxLevel10;
			case NurApi.TXLEVEL_11:
				return TxLevel11;
			case NurApi.TXLEVEL_12:
				return TxLevel12;
			case NurApi.TXLEVEL_13:
				return TxLevel13;
			case NurApi.TXLEVEL_14:
				return TxLevel14;
			case NurApi.TXLEVEL_15:
				return TxLevel15;
			case NurApi.TXLEVEL_16:
				return TxLevel16;
			case NurApi.TXLEVEL_17:
				return TxLevel17;
			case NurApi.TXLEVEL_18:
				return TxLevel18;
			case NurApi.TXLEVEL_19:
				return TxLevel19;
			case NurApi.TXLEVEL_20:
				return TxLevel20;
			case NurApi.TXLEVEL_21:
				return TxLevel21;
			case NurApi.TXLEVEL_22:
				return TxLevel22;
			case NurApi.TXLEVEL_23:
				return TxLevel23;
			case NurApi.TXLEVEL_24:
				return TxLevel24;
			case NurApi.TXLEVEL_25:
				return TxLevel25;
			case NurApi.TXLEVEL_26:
				return TxLevel26;
			case NurApi.TXLEVEL_27:
				return TxLevel27;
			case 255:
				return TxLevelDefault;

			default:
				throw new IllegalArgumentException(
						"No enum constant found for value: " + constVal);
			}
		}
	}

	/**
	 * This enum contains supported regions and maps the region code to the Nur
	 * API constant.
	 * 
	 */
	protected enum Region {
		/**
		 * Region entry for Europe with region code "eu" and Nur API constant
		 * NurApi.REGIONID_EU
		 */
		RegionEU("EU", NurApi.REGIONID_EU), RegionFCC("FCC",
				NurApi.REGIONID_FCC), RegionJapan("Japan",
				NurApi.REGIONID_JA250MW), RegionChina("China",
				NurApi.REGIONID_PRC),

		/**
		 * Region entry for unspecified regions with region code "unspecified"
		 * and Nur API constant NurApi.REGIONID_CUSTOM
		 */
		RegionUnspecified("Unspecified", NurApi.REGIONID_CUSTOM);

		final String regionCode;
		final int nurApiRegion;

		private Region(String regionCode, int nurApiRegion) {
			this.regionCode = regionCode;
			this.nurApiRegion = nurApiRegion;
		}

		/**
		 * Returns a {@link Region} instance for a specific Nur API constant. If
		 * the passed constant is not valid, RegionUnspecified is returned.
		 * 
		 * @param nurApiRegion
		 *            a NUR Api region ID constant (e.g. NurApi.REGIONID_EU,
		 *            NurApi.REGIONID_FCC)
		 * @return a {@link Region} instance
		 */
		static Region fromNurApiRegion(int nurApiRegion) {
			switch (nurApiRegion) {
			case NurApi.REGIONID_EU:
				return RegionEU;
			case NurApi.REGIONID_FCC:
				return RegionFCC;
			case NurApi.REGIONID_JA250MW:
				return RegionJapan;
			case NurApi.REGIONID_PRC:
				return RegionChina;

			case NurApi.REGIONID_AUSTRALIA:
			case NurApi.REGIONID_BRAZIL:
			case NurApi.REGIONID_CUSTOM:
			case NurApi.REGIONID_INDIA:
			case NurApi.REGIONID_JA500MW:
			case NurApi.REGIONID_KOREA_LBT:
			case NurApi.REGIONID_MALAYSIA:
			case NurApi.REGIONID_NEWZEALAND:
			case NurApi.REGIONID_PHILIPPINES:
			case NurApi.REGIONID_RUSSIA:
			case NurApi.REGIONID_SINGAPORE:
			case NurApi.REGIONID_THAILAND:
			case NurApi.REGIONID_VIETNAM:

			default:
				return RegionUnspecified;
			}
		}

		/**
		 * Returns a {@link Region} instance for a specific region code. If the
		 * passed constant is not valid, RegionUnspecified is returned.
		 * 
		 * @param regionCode
		 *            a region code as String constant (e.g. "EU",
		 *            "unspecified")
		 * @return a {@link Region} instance.
		 */
		static Region fromRegionCode(String regionCode) {
			switch (regionCode) {
			case "EU":
				return RegionEU;
			case "FCC":
				return RegionFCC;
			case "Japan":
				return RegionJapan;
			case "China":
				return RegionChina;

			default:
				return RegionUnspecified;
			}
		}
	}


}
