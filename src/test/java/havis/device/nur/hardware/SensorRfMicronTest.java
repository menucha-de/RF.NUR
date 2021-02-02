package havis.device.nur.hardware;

import havis.device.rf.RFConsumer;
import havis.device.rf.RFDevice;
import havis.device.rf.common.CommunicationHandler;
import havis.device.rf.common.util.RFUtils;
import havis.device.rf.configuration.AntennaConfiguration;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.device.rf.configuration.ConnectType;
import havis.device.rf.configuration.InventorySettings;
import havis.device.rf.nur.NurHardwareManager;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.result.ReadResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.nordicid.nurapi.CustomExchangeParams;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurCmd;
import com.nordicid.nurapi.NurCmdCustomExchange;
import com.nordicid.nurapi.NurIRConfig;
import com.nordicid.nurapi.NurInventoryExtended;
import com.nordicid.nurapi.NurInventoryExtendedFilter;
import com.nordicid.nurapi.NurTag;
import com.nordicid.nurapi.NurTagStorage;

public class SensorRfMicronTest {

	public static void main(String[] args) throws Exception {
		RFDevice rfDevice = new CommunicationHandler();
		RFConsumer rfConsumer = new RFConsumer() {
			@Override
			public void keepAlive() {
			}

			@Override
			public List<TagOperation> getOperations(TagData arg0) {
				return null;
			}

			@Override
			public void connectionAttempted() {
			}
		};

		rfDevice.openConnection(rfConsumer, 50);

		List<Short> antennas = new ArrayList<>();
		List<TagOperation> operations = new ArrayList<>();
		operations.add(new ReadOperation("usr", s(3), s(128 / 16), s(64 / 16), s(0)));
		List<Filter> filters = new ArrayList<>();

		rfDevice.setRegion("EU");

		List<Configuration> config = new ArrayList<>();
		AntennaConfiguration a1 = new AntennaConfiguration();
		a1.setConnect(ConnectType.TRUE);
		a1.setId(s(1));
		a1.setChannelIndex(s(0));
		a1.setTransmitPower(s(19));
		a1.setReceiveSensitivity(s(0));
		a1.setHopTableID(s(0));
		config.add(a1);
		AntennaConfiguration a2 = new AntennaConfiguration();
		a2.setConnect(ConnectType.TRUE);
		a2.setId(s(2));
		a2.setChannelIndex(s(0));
		a2.setTransmitPower(s(19));
		a2.setReceiveSensitivity(s(0));
		a2.setHopTableID(s(0));
		config.add(a2);
		rfDevice.setConfiguration(config);

		InventorySettings invSettings = (InventorySettings) rfDevice.getConfiguration(ConfigurationType.INVENTORY_SETTINGS, s(0), s(0), s(0)).get(0);
		invSettings.getRssiFilter().setMinRssi(s(0));
		invSettings.getRssiFilter().setMaxRssi(s(64));
		invSettings.getSingulationControl().setQValue(s(1));
		invSettings.getSingulationControl().setRounds(s(1));
		invSettings.getSingulationControl().setSession(s(1));
		invSettings.getSingulationControl().setTransitTime(s(0));
		invSettings.getSelectionMasks().clear();
		rfDevice.setConfiguration(Arrays.asList((Configuration) invSettings));

		System.out.println("Please sensor tag on antenna 1.");
		anyKey();
		System.out.println();

		byte[] calibrationData = null;

		antennas.clear();
		antennas.add(s(1));
		int last = -1;
		for (int i = 0; i < 10; i++) {
			List<TagData> tags = rfDevice.execute(antennas, filters, operations);
			if (tags.size() != last) {
				last = tags.size();
				System.out.println("Tags: " + tags.size());
			}
			for (TagData tag : tags) {
				System.out.println(tag.getAntennaID() + ": " + RFUtils.bytesToHex(tag.getEpc()));
				if (calibrationData == null || calibrationData.length == 0) {
					calibrationData = ((ReadResult) tag.getResultList().get(0)).getReadData();
					System.out.println("Calibration data: " + RFUtils.bytesToHex(calibrationData));
				}
			}
		}

		if (calibrationData != null && calibrationData.length != 0) {

			double code1 = toLong(strip(calibrationData, 16, 12), 12);
			double temp1 = toLong(strip(calibrationData, 28, 11), 11);
			double code2 = toLong(strip(calibrationData, 39, 12), 12);
			double temp2 = toLong(strip(calibrationData, 51, 11), 11);

			CommunicationHandler h = (CommunicationHandler) rfDevice;
			java.lang.reflect.Field f = h.getClass().getDeclaredField("mainController");
			f.setAccessible(true);
			Object controller = f.get(h);

			java.lang.reflect.Field f2 = controller.getClass().getDeclaredField("hwManager");
			f2.setAccessible(true);
			NurHardwareManager m = (NurHardwareManager) f2.get(controller);

			java.lang.reflect.Field f3 = m.getClass().getDeclaredField("nurApi");
			f3.setAccessible(true);
			NurApi api = (NurApi) f3.get(m);

			System.out.println();
			System.out.println("### START CUSTOM EXCHANGE NOW");
			anyKey();
			System.out.println();

			// Custom code

			java.lang.reflect.Method exchangeCommandMethod = NurApi.class.getDeclaredMethod("exchangeCommand", NurCmd.class);
			exchangeCommandMethod.setAccessible(true);

			CustomExchangeParams tempSelect = new CustomExchangeParams();
			tempSelect.bitBuffer = new byte[NurApi.MAX_BITSTR_BITS / 8];
			int txLen = 0;
			txLen = NurApi.bitBufferAddValue(tempSelect.bitBuffer, 0xA, 4, txLen);
			txLen = NurApi.bitBufferAddValue(tempSelect.bitBuffer, 0x0, 3, txLen);
			txLen = NurApi.bitBufferAddValue(tempSelect.bitBuffer, 0x1, 3, txLen);
			txLen = NurApi.bitBufferAddValue(tempSelect.bitBuffer, 0x3, 2, txLen);
			txLen = NurApi.bitBufferAddEBV32(tempSelect.bitBuffer, 0xE0, txLen);
			txLen = NurApi.bitBufferAddValue(tempSelect.bitBuffer, 0x0, 8, txLen);
			txLen = NurApi.bitBufferAddValue(tempSelect.bitBuffer, 0x0, 1, txLen);
			tempSelect.txLen = txLen;
			tempSelect.asWrite = true;
			tempSelect.txOnly = true;
			tempSelect.noTxCRC = false;
			tempSelect.rxLen = 0;
			tempSelect.rxTimeout = 20;
			tempSelect.appendHandle = false;
			tempSelect.xorRN16 = false;
			tempSelect.noRxCRC = false;
			tempSelect.rxLenUnknown = false;
			tempSelect.txCRC5 = false;
			tempSelect.rxStripHandle = false;

			NurInventoryExtended inventoryParams = new NurInventoryExtended();
			inventoryParams.inventorySelState = 0;
			inventoryParams.inventoryTarget = 0;
			inventoryParams.Q = 0;
			inventoryParams.rounds = 5;
			inventoryParams.session = NurApi.SESSION_S0;

			NurIRConfig irConfig = new NurIRConfig();
			irConfig.IsRunning = true;
			irConfig.irType = NurApi.IRTYPE_EPCDATA;
			irConfig.irBank = NurApi.BANK_PASSWD;
			irConfig.irAddr = 0xE;
			irConfig.irWordCount = 1;
			api.setIRConfig(irConfig);

			for (int cycle = 1; cycle <= 1000; cycle++) {
				try {
					api.inventory(1, 1, 0);
					api.clearIdBuffer(true);
					api.setExtendedCarrier(true);
					exchangeCommandMethod.invoke(api, new NurCmdCustomExchange(api, 0, false, tempSelect));
					Thread.sleep(3);
					System.out.println();
					System.out.println("Read attempt " + cycle);
					api.inventoryExtended(inventoryParams, new NurInventoryExtendedFilter[0], 0);
					api.fetchTags(true);
					NurTagStorage tagStorage = api.getStorage();
					for (int i = 0; i < tagStorage.size(); i++) {
						NurTag tag = tagStorage.get(i);
						byte[] irData = tag.getIrData();
						System.out.println("EPC: " + tag.getEpcString().toUpperCase());
						if (irData.length >= 2) {
							double temperatureCode = (irData[0] & 0xFF) * 256 + (irData[1] & 0xFF);
							if (temperatureCode > 1000 && temperatureCode < 3500) {
								System.out.println("Temperature Code: " + temperatureCode);
								double temperature = 1.0 / 10.0 * ((((temp2 - temp1) / (code2 - code1)) * (temperatureCode - code1)) + temp1 - 800.0);
								System.out.println("Temperature: " + temperature);
							}
						}
					}

				} catch (Exception e) {
					System.out.println(e);
				}
				api.setExtendedCarrier(false);
				api.clearIdBuffer(true);
			}
		}
		rfDevice.closeConnection();
	}

	public static int size(int length) {
		return size(length, 8);
	}

	public static int size(int length, int size) {
		if ((length > -1) && (length < Integer.MAX_VALUE)) {
			return (length + (size - length % size) % size) / size;
		} else {
			throw new IllegalArgumentException("Length should be greater or equal to zero and lower then max value of integer minus size");
		}
	}

	public static byte[] strip(byte[] bytes, int offset, int length) {
		if ((offset + length) / 8 + ((offset + length) % 8 == 0 ? 0 : 1) <= bytes.length) {
			int l = length == 0 ? bytes.length - offset / 8 : size(length);
			// number of bytes of the result data
			byte[] result = new byte[l];

			// skip bytes depending on field offset
			int byteIndex = offset / 8;
			int byteLength = byteIndex + (length == 0 ? bytes.length - offset / 8 : size(offset % 8 + length));

			// number of bits to move within a byte
			offset = offset % 8;
			if (byteIndex < byteLength) {
				for (int i = 0; i < result.length; i++) {
					// move current byte offset bits left and add next byte, if
					// offset greater then zero move next byte (8 - offset) bits
					// right
					result[i] = (byte) (((bytes[byteIndex] & 0xFF) << offset) + ((++byteIndex < byteLength) && offset > 0 ? (bytes[byteIndex] & 0xFF) >> (8 - offset)
							: 0));
				}
				if (length % 8 > 0) {
					// blank last bits
					int current = result[result.length - 1] & 0xFF;
					current &= 0xFF << (8 - length % 8);
					result[result.length - 1] = (byte) current;
				}
			}
			return result;
		} else {
			throw new IndexOutOfBoundsException("Offset plus length must not be greater than bytes.lenght");
		}
	}

	public static long toLong(byte[] data, int length) {
		long l = 0;
		for (byte b : data) {
			l = (b & 0xFF) + (l << 8);
		}
		return l >> (8 - length % 8) % 8;
	}

	private static short s(int i) {
		return (short) i;
	}

	private static void anyKey() {
		System.out.print("Press ENTER to continue.");
		try {
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
