package havis.device.rf.nur;

import havis.device.rf.RFConsumer;
import havis.device.rf.capabilities.RegulatoryCapabilities;
import havis.device.rf.capabilities.TransmitPowerTable;
import havis.device.rf.capabilities.TransmitPowerTableEntry;
import havis.device.rf.common.Environment;
import havis.device.rf.common.HardwareManager;
import havis.device.rf.common.util.RFUtils;
import havis.device.rf.common.util.RFUtils.OperationListInspectionResult;
import havis.device.rf.configuration.AntennaConfiguration;
import havis.device.rf.configuration.AntennaConfigurationList;
import havis.device.rf.configuration.AntennaProperties;
import havis.device.rf.configuration.AntennaPropertyList;
import havis.device.rf.configuration.ConnectType;
import havis.device.rf.configuration.RFRegion;
import havis.device.rf.configuration.RssiFilter;
import havis.device.rf.configuration.SingulationControl;
import havis.device.rf.exception.ConnectionException;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.nur.Constants.Antenna;
import havis.device.rf.nur.Constants.Region;
import havis.device.rf.nur.Constants.TxLevel;
import havis.device.rf.nur.NurErrorMap.RFCError;
import havis.device.rf.nur.NurTagProcessor.EpcBankData;
import havis.device.rf.nur.NurTagProcessor.Singulation;
import havis.device.rf.nur.NurTagProcessor.Singulation.SingulationStrategy;
import havis.device.rf.nur.firmware.ExecutionException;
import havis.device.rf.nur.firmware.FirmwareUpdater;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.TagDataList;
import havis.device.rf.tag.operation.CustomOperation;
import havis.device.rf.tag.operation.KillOperation;
import havis.device.rf.tag.operation.LockOperation;
import havis.device.rf.tag.operation.LockOperation.Field;
import havis.device.rf.tag.operation.LockOperation.Privilege;
import havis.device.rf.tag.operation.ReadOperation;
import havis.device.rf.tag.operation.RequestOperation;
import havis.device.rf.tag.operation.TagOperation;
import havis.device.rf.tag.operation.WriteOperation;
import havis.device.rf.tag.result.CustomResult;
import havis.device.rf.tag.result.KillResult;
import havis.device.rf.tag.result.LockResult;
import havis.device.rf.tag.result.OperationResult;
import havis.device.rf.tag.result.ReadResult;
import havis.device.rf.tag.result.ReadResult.Result;
import havis.device.rf.tag.result.WriteResult;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.nordicid.nativeserial.NativeSerialTransport;
import com.nordicid.nativeserial.SerialPort;
import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurGPIOConfig;
import com.nordicid.nurapi.NurInventoryExtended;
import com.nordicid.nurapi.NurInventoryExtendedFilter;
import com.nordicid.nurapi.NurRespInventory;
import com.nordicid.nurapi.NurRespReaderInfo;
import com.nordicid.nurapi.NurTag;
import com.nordicid.nurapi.NurTagStorage;
import com.nordicid.nurapi.NurTuneResponse;

/**
 * A hardware specific implementation of the {@link HardwareManager} interface
 * using the NordicID NUR API.
 * 
 */
public class NurHardwareManager implements HardwareManager {
	private static final Logger log = Logger.getLogger(NurHardwareManager.class.getName());

	private static final String ID_VENDOR = "idVendor";
	private static final String ID_PRODUCT = "idProduct";

	private static final String NUR_VENDOR = "04e6";
	private static final String NUR_PRODUCT = "0112";

	private NurApiListenerImpl nurApiListener = new NurApiListenerImpl();
	private NativeSerialTransport transport = null;
	private SerialPort serialPort = null;
	private NurApi nurApi = null;
	private NurConfigurationHelper setup;
	private List<Short> connectedAntennas = new ArrayList<>();

	/**
	 * Creates an instance of this class.
	 */
	public NurHardwareManager() {
		super();
		log.log(Level.FINE, "{0} instantiated.", this.getClass().getName());
	}

	/**
	 * Creates an instance of the NUR API and opens a connection to the
	 * underlying RFID hardware using the native serial transport. Additionally,
	 * the module setup is loaded and with that the
	 * {@link NurConfigurationHelper} is started. Once the connection is
	 * established some detailed technical information on the RFID module is
	 * written to the "Info" log level.
	 */
	@Override
	public void openConnection() throws ConnectionException, ImplementationException {
		log.entering(this.getClass().getName(), "openConnection");

		if (nurApi != null && nurApi.isConnected()) {
			log.finer("Using existing connection to RFID hardware.");
			log.exiting(this.getClass().getName(), "openConnection");
			return;
		}
		
		createDevice();

		log.finer("Establishing new connection to RFID hardware.");

		this.serialPort = new SerialPort(Environment.SERIAL_DEVICE_PATH, Environment.SERIAL_DEVICE_PATH, 0);
		this.transport = new NativeSerialTransport(serialPort, Environment.SERIAL_DEVICE_BAUDRATE.getValue());

		this.nurApi = new NurApi(transport);
		this.nurApi.setListener(this.nurApiListener);
		this.setup = new NurConfigurationHelper(this.nurApi);

		try {
			this.nurApi.connect();
			this.setup.loadModuleSetup();
		} catch (Exception ex) {
			throw new ConnectionException(ex);
		}

		if (log.isLoggable(Level.INFO)) {
			try {
				NurRespReaderInfo info = this.nurApi.getReaderInfo();
				log.log(Level.INFO, "Reader info (Name): {0}", info.name);
				log.log(Level.INFO, "Reader info (Hardware version): {0}", info.hwVersion);
				log.log(Level.INFO, "Reader info (Firmware version): {0}", info.swVersion);
				log.log(Level.INFO, "Reader info (API version): {0}", this.nurApi.getFileVersion());
				log.log(Level.INFO, "Reader info (Struct version): {0}", info.version);
				log.log(Level.INFO, "Reader info (Serial no.): {0}", info.serial);
				log.log(Level.INFO, "Reader info (FCC ID): {0}", info.fccId);
			} catch (Exception ex) {
				log.log(Level.INFO, "Failed to retrieve reader info: {0}", ex);
			}
		}
		
		log.exiting(this.getClass().getName(), "openConnection");
	}

	private static void createDevice() {
		if (!new File(Environment.SERIAL_DEVICE_PATH).exists()) {
			log.finer("Creating serial device at " + Environment.SERIAL_DEVICE_PATH);

			if (hasNurUsbDevice()) {
				try {
					String command = "mknod -m 600 " + Environment.SERIAL_DEVICE_PATH + " c 166 0";
					Process process = Runtime.getRuntime().exec(command);
					int exitCode;
					if ((exitCode = process.waitFor()) != 0) {
						log.log(Level.SEVERE, "Failed to create serial device at " + Environment.SERIAL_DEVICE_PATH + ": \"" + command
								+ "\" failed with exit code " + exitCode);
					}
				} catch (IOException | InterruptedException e) {
					log.log(Level.SEVERE, "Failed to create serial device at " + Environment.SERIAL_DEVICE_PATH, e);
				}
			}
		}
	}

	private static boolean hasNurUsbDevice() {
		File deviceRoot = new File("/sys/bus/usb/devices");
		File[] devices = deviceRoot.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) {
					String[] idFiles = file.list(new FilenameFilter() {
						@Override
						public boolean accept(File dir, String name) {
							return ID_VENDOR.equals(name) || ID_PRODUCT.equals(name);
						}
					});
					return idFiles != null && idFiles.length == 2;
				}
				return false;
			}
		});
		if (devices != null) {
			for (File device : devices) {
				String vendor = readDevice(device, ID_VENDOR);
				String product = readDevice(device, ID_PRODUCT);
				if (NUR_VENDOR.equals(vendor) && NUR_PRODUCT.equals(product)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String readDevice(File device, String attribute) {
		try {
			List<String> lines = Files.readAllLines(new File(device, attribute).toPath(), Charset.defaultCharset());
			if (lines != null && lines.size() > 0)
				return lines.get(0).toLowerCase();
		} catch (IOException e) {
			log.log(Level.SEVERE, "Failed to read USB device attribute '" + attribute + "' from '" + device.getAbsolutePath() + "'", e);
		}
		return null;
	}

	/**
	 * Closes the connection to the underlying RFID hardware. It may take about
	 * 20 seconds until the disconnected event is fired and the connection has
	 * been closed. A semaphore avoids any concurrent access until the
	 * disconnect process is finished. A log entry is written on debug level
	 * when the connection has been closed.
	 * 
	 */
	@Override
	public void closeConnection() throws ConnectionException {
		log.entering(this.getClass().getName(), "closeConnection");

		if (this.nurApi != null) {
			try {
				if (this.nurApi.isConnected()) {
					log.finer("Closing connection to RFID hardware.");

					this.nurApi.disconnect();
					this.nurApiListener.waitForDisconnectedEvent();
				}

				if (this.transport.isConnected())
					this.transport.disconnect();

			} catch (Exception ex) {
				throw new ConnectionException(ex);
			} finally {
				this.nurApi.dispose();
			}

			this.nurApi = null;
			this.transport = null;
			this.serialPort = null;

			log.finer("Connection to RFID hardware closed");
		}
		
		log.exiting(this.getClass().getName(), "closeConnection");
	}

	/**
	 * Executes a set of {@link TagOperation} objects using a set of antennas on
	 * all transponders matching a set of {@link Filter} instances. The results
	 * are written to the given {@link ResultManager} instance.
	 * 
	 * @param antennas
	 *            a {@link List} of antenna IDs to be used for the execute. A
	 *            shortcut to imply that all antennas are to be used, the list
	 *            can contain the single value 0.
	 * 
	 * @param filters
	 *            a {@link List} of {@link Filter} allowing to limit the amount
	 *            of transponders to execute the operations on. If no filters is
	 *            to be applied, this list can be empty (but not null).
	 * 
	 * @param operations
	 *            a {@link List} of {@link TagOperation} objects. These
	 *            operations are performed on all transponders matching the
	 *            optional filters in the order as they appear in the operation
	 *            list.
	 * 
	 * @param consumer
	 *            an implementation of the {@link RFConsumer} interface. This
	 *            instance is used to acquire the {@link TagOperation} instances
	 *            belonging to a {@link RequestOperation}. If the list of
	 *            operations does not contain any request operations, this
	 *            instance is not used.
	 * @return a {@link TagDataList} instance containing all transponders found
	 *         during inventory including a list of operation results for each
	 *         transponder.
	 * 
	 * @throws {@link
	 *             ImplementationException} if an unexpected error during the
	 *             execution of a tag operation occurs. Expected errors are
	 *             caught and a corresponding error is written to the
	 *             {@link OperationResult}. This kind of exception is also
	 *             thrown when accessing the environment properties or applying
	 *             the antenna selection fails.
	 * 
	 * @throws {@link
	 *             ParameterException} if for a {@link LockOperation} an
	 *             undefined {@link Privilege} or {@link Field} is used.
	 * 
	 */
	@Override
	public TagDataList execute(List<Short> antennas, List<Filter> filters, List<TagOperation> operations, RFConsumer consumer)
			throws ImplementationException, ParameterException {
				
		if (log.isLoggable(Level.FINER))
			log.entering(this.getClass().getName(), "execute", 
				new Object[] { RFUtils.serializeList(antennas, Short.class), RFUtils.serializeList(filters, Filter.class),
				RFUtils.serializeList(operations, TagOperation.class), consumer });
		
		TagDataList result = new TagDataList();
		
		if (this.setup.getRegion() == Region.RegionUnspecified) {
			log.finer("Module region is set to 'Unspecified'. Aborting execution.");
			return result;
		}

		antennas = getEffectiveAntennaList(antennas);
		if (antennas.size() == 0) {
			log.finer("Empty antenna list received. Aborting execution.");
			return result;
		}
		
		log.finer("Using effective antennas: " + antennas);

		short antennaCount = this.setup.applyAntennaSelection(antennas);
		if (antennaCount == 0) {
			log.finer("No active antenna selected. Aborting execution.");
			return result;
		}

		NurTagProcessor tagProcessor = new NurTagProcessor(this.nurApi);

		Singulation sing = tagProcessor.getOptimalSingulation(filters);
		log.log(Level.FINER, "Using singulation strategy: {0}", sing.getStrategy());

		OperationListInspectionResult opListInspResult = null;
		if (Environment.OPTIMIZED_TID_BANK_READING)
			opListInspResult = RFUtils.inspectOperationList(operations);

		NurTagStorage storage = null;
		try {
			storage = this.inventory(filters, sing, opListInspResult);			
		} 
		/* internal NUR API issues we cannot handle should result in empty storage (as if no tag has been found) */
		catch (TimeoutException | ArrayIndexOutOfBoundsException | IOException ex) { 
			storage = new NurTagStorage();			
		}
		catch (Exception e) {			
			LogRecord logRec = new LogRecord(Level.SEVERE, "Failed to execute inventory: {0}");
			logRec.setThrown(e);
			logRec.setParameters(new Object[] { e });
			logRec.setLoggerName(log.getName());
			log.log(logRec);
			return result;
		}
		
		final byte[] em4325Tid = new byte[] { (byte)0xe2, (byte)0x80, (byte)0xb0, 0x40 };
		
		boolean em4325InField = false;
		for (int tagIndex = 0; tagIndex < storage.size(); tagIndex++) {
			NurErrorMap.RFCError tagError = null;
			NurTag tag = storage.get(tagIndex);
			
			if (!em4325InField && tag.getIrData() != null && tag.getIrData().length >= 4) { // IR data contains at least 2 words
				em4325InField = (tag.getIrData()[1] == em4325Tid[1] && tag.getIrData()[2] == em4325Tid[2] && tag.getIrData()[3] == em4325Tid[3]);
				if (em4325InField)
					log.log(Level.INFO, "EM4325 tag detected.");
			}			
			
			TagData tagData = new TagData();
			tagData.setTagDataId(tagIndex);
			tagData.setAntennaID((short) (tag.getAntennaId() + 1));
			tagData.setChannel((short) tag.getChannel());
			tagData.setRssi(tag.getRssi());
			tagData.setResultList(new ArrayList<OperationResult>());
			result.getEntryList().add(tagData);

			EpcBankData epcData = tagProcessor.readEpcBankData(sing, tag, false);
			if (epcData.getResult() == Result.SUCCESS) {
				tagData.setCrc(epcData.getCrc());
				tagData.setPc(epcData.getPc());
				tagData.setEpc(epcData.getEpc());
				tagData.setXpc(epcData.getXpc());
			} else {
				tagData.setEpc(new byte[] {});
				tagError = NurErrorMap.RFCError.NonSpecificTagError;
			}

			if (sing.getStrategy() == Singulation.SingulationStrategy.EPC)
				sing = Singulation.getInstance(SingulationStrategy.EPC, tag);

			if (em4325InField && !operations.isEmpty()) resetInventoriedState();
			
			for (TagOperation op : operations)
				tagError = performOperation(op, tagProcessor, tagData, sing, tagError, tag, consumer);
			
			if (em4325InField && !operations.isEmpty()) resetInventoriedState();
		}

		if (log.isLoggable(Level.FINER))
			log.exiting(this.getClass().getName(), "execute", RFUtils.serializeList(result.getEntryList(), TagData.class));
		
		return result;
	}

	private void resetInventoriedState() {
		try {
			nurApi.resetToTarget(NurApi.SESSION_S0, true);
		} catch (Exception e) {
			LogRecord logRec = new LogRecord(Level.FINE, "Failed to reset inventoried state: {0}");
			logRec.setThrown(e);
			logRec.setParameters(new Object[] { e });
			logRec.setLoggerName(log.getName());
			log.log(logRec);
		}
	}

	List<Short> getEffectiveAntennaList(List<Short> antennas) throws ImplementationException {
		
		if (log.isLoggable(Level.FINER))
			log.entering(getClass().getName(), "getEffectiveAntennaList", RFUtils.serializeList(antennas, Short.class));
		
		if (Environment.HARDWARE_MANAGER_ANTENNAS != null)
			antennas = Environment.HARDWARE_MANAGER_ANTENNAS;

		List<Short> result = null;
		
		if (antennas.size() == 0)
			result = antennas;

		else if (antennas.get(0) == 0)
			result = this.connectedAntennas;

		else {
			result = new ArrayList<>();
			for (Short s : antennas)
				if (connectedAntennas.contains(s)) 
					result.add(s);
		}

		if (log.isLoggable(Level.FINER))
			log.exiting(getClass().getName(), "getEffectiveAntennaList", RFUtils.serializeList(result, Short.class));
		
		return result;
	}

	/**
	 * Performs a G1C2 tag operation. This method is used by the execute method
	 * to perform operations.
	 * 
	 * @param op
	 *            the {@link TagOperation} instance to be performed
	 * @param tagProcessor
	 *            the {@link NurTagProcessor} instance that does performs the
	 *            actual operation using the NUR API.
	 * @param tagData
	 *            a {@link TagData} instance used to write the operation result
	 *            to.
	 * @param sing
	 *            a {@link Singulation} instance used to to identify the
	 *            transponder.
	 * @param tagError
	 *            a reference to the previous {@link RFCError} or null if error
	 *            occurred so far.
	 * @param nurTag
	 *            NurTag instance representing transponder
	 * @param consumer
	 *            an implementation of the {@link RFConsumer} interface. This
	 *            instance is used to acquire the {@link TagOperation} instances
	 *            belonging to a {@link RequestOperation}. If the list of
	 *            operations does not contain any request operations, this
	 *            instance is not used.
	 * @return an RFCError instance is a non-critical error occurred during
	 *         operation or null, if no error occurred.
	 * 
	 * @throws ImplementationException
	 *             If the access to the {@link Environment} properties fails or
	 *             an error occurred during update of the {@link ResultManager}
	 * @throws ParameterException
	 *             If a {@link LockOperation} is called with an undefined
	 *             {@link Privilege} or {@link Field} parameter.
	 */
	RFCError performOperation(TagOperation op, NurTagProcessor tagProcessor, TagData tagData, Singulation sing,
			RFCError tagError, NurTag nurTag, RFConsumer consumer) throws ImplementationException, ParameterException {

		if (log.isLoggable(Level.FINER))
			log.entering(this.getClass().getName(), "performOperation", new Object [] { 
				RFUtils.serialize(op), tagProcessor, RFUtils.serialize(tagData), 
				RFUtils.serialize(sing), tagError, nurTag, consumer });

		if (op instanceof ReadOperation) {
			ReadResult rRes = null;
			ReadOperation rdOp = (ReadOperation) op;
			if (tagError == null) {
				rRes = rdOp.getLength() == 0 ? 
						tagProcessor.readCompleteBank(sing, rdOp, tagData, nurTag, Environment.OPTIMIZED_TID_BANK_READING ? nurTag.getIrData() : null) : 
						tagProcessor.read(sing, rdOp, Environment.OPTIMIZED_TID_BANK_READING ? nurTag.getIrData() : null);

				if (rRes.getResult() != ReadResult.Result.SUCCESS)
					tagError = NurErrorMap.RFCError.NonSpecificTagError;
			} else {
				rRes = new ReadResult();
				rRes.setReadData(new byte[] {});
				rRes.setOperationId(op.getOperationId());
				rRes.setResult(NurErrorMap.rfcErrorToReadResult(tagError));
			}

			tagData.getResultList().add(rRes);
		}

		else if (op instanceof WriteOperation) {
			WriteResult wRes = null;
			WriteOperation wrOp = (WriteOperation) op;
			if (tagError == null) {
				wRes = tagProcessor.write(sing, wrOp);

				if (wrOp.getBank() == RFUtils.BANK_EPC && Environment.HANDLE_TRANSPONDER_EPC_CHANGE) {
					byte[] newEpc = sing.epcChanged(nurTag, wrOp);
					if (log.isLoggable(Level.FINER))
						log.log(Level.FINER, "Stored changed EPC '{0}' in tag object with former EPC '{1}'", 
							new Object[] { RFUtils.bytesToHex(newEpc), nurTag.getEpcString() });
				}

				if (wRes.getResult() != WriteResult.Result.SUCCESS)
					tagError = NurErrorMap.RFCError.NonSpecificTagError;

			} else {
				wRes = new WriteResult();
				wRes.setOperationId(op.getOperationId());
				wRes.setResult(NurErrorMap.rfcErrorToWriteResult(tagError));
			}

			tagData.getResultList().add(wRes);
		}

		else if (op instanceof LockOperation) {
			LockResult lRes = null;
			LockOperation lOp = (LockOperation) op;
			if (tagError == null) {
				lRes = tagProcessor.lock(sing, lOp);
				if (lRes.getResult() != LockResult.Result.SUCCESS)
					tagError = NurErrorMap.RFCError.NonSpecificTagError;
			} else {
				lRes = new LockResult();
				lRes.setOperationId(op.getOperationId());
				lRes.setResult(NurErrorMap.rfcErrorToLockResult(tagError));
			}

			tagData.getResultList().add(lRes);
		}

		else if (op instanceof KillOperation) {
			KillResult kRes = null;
			KillOperation kOp = (KillOperation) op;
			if (tagError == null) {
				kRes = tagProcessor.kill(sing, kOp);
				if (kRes.getResult() != KillResult.Result.SUCCESS)
					tagError = NurErrorMap.RFCError.NonSpecificTagError;
			} else {
				kRes = new KillResult();
				kRes.setOperationId(op.getOperationId());
				kRes.setResult(NurErrorMap.rfcErrorToKillResult(tagError));
			}

			tagData.getResultList().add(kRes);
			
		} else if (op instanceof CustomOperation) {
			CustomResult cRes = null;
			CustomOperation cOp = (CustomOperation) op;
			if (tagError == null) {
				cRes = tagProcessor.custom(nurTag, sing, cOp);
				if (cRes.getResult() != CustomResult.Result.SUCCESS)
					tagError = NurErrorMap.RFCError.NonSpecificTagError;
			} else {
				cRes = new CustomResult();
				cRes.setOperationId(op.getOperationId());
				cRes.setResult(NurErrorMap.rfcErrorToCustomResult(tagError));
			}
			tagData.getResultList().add(cRes);
		} else if (op instanceof RequestOperation && consumer != null) {
			log.log(Level.FINER, "Request operation received, reqesting additional ops from consumer: {0} ", consumer);

			/*
			 * get additional operations from consumer only if not error
			 * occurred so far, otherwise create a new empty list.
			 */
			List<TagOperation> additionalOps = tagError == null ? consumer.getOperations(tagData) : new ArrayList<TagOperation>();

			if (log.isLoggable(Level.FINER)) {
				log.log(Level.FINER, "Additional ops from consumer received: {0} ", RFUtils.serializeList(additionalOps, TagOperation.class));

				if (tagData.getResultList().size() > 0) {
					OperationResult opRes = tagData.getResultList().get(0);
					if (opRes instanceof ReadResult) {
						ReadResult rdRes = (ReadResult) tagData.getResultList().get(0);
						if (log.isLoggable(Level.FINER)) 
							log.log(Level.FINER, "Read result data used: {0} ", RFUtils.bytesToHex(rdRes.getReadData()));
					} else
						log.log(Level.FINER, "Operation result in TagData object is no instance of ReadResult but {0}.", opRes.getClass());
				} else
					log.finer("TagData object has empty result list.");
			}

			for (TagOperation additionalOp : additionalOps)
				// to avoid recursive request ops, we pass null as consumer
				tagError = performOperation(additionalOp, tagProcessor, tagData, sing, tagError, nurTag, null);
		}

		if (log.isLoggable(Level.FINER))
			log.exiting( this.getClass().getName(), "performOperation", new Object [] { tagError, RFUtils.serialize(tagData) });

		return tagError;
	}

	/**
	 * Performs an inventory round gathering all transponders in the field on
	 * all enabled antennas.
	 * 
	 * @param filters
	 *            an optional {@link List} of {@link Filter} instances limiting
	 *            the amount of transponders.
	 * @param sing
	 *            a {@link Singulation} instance defining which additional data
	 *            is to be read during inventory.
	 * @param opListInspResult
	 *            an instance of {@link OperationListInspectionResult}
	 *            containing information if additional data is to be read during
	 *            inventory needed for optimized TID reading.
	 * 
	 * @return a NurTagStorage instance containing NurTag objects representing
	 *         the transponders in the field.
	 * 
	 * @throws Exception
	 *             if something goes wrong when calling methods of the NUR API.
	 */
	NurTagStorage inventory(List<Filter> filters, Singulation sing, OperationListInspectionResult opListInspResult) throws Exception {
		long now = new Date().getTime();
		
		if (log.isLoggable(Level.FINER))
			log.entering(this.getClass().getName(), "inventory", new Object[] { RFUtils.serializeList(filters, Filter.class), RFUtils.serialize(sing), opListInspResult } );			

		this.nurApi.clearIdBuffer(true);		
		
		/* if singulation strategy is TID, read secure amount of words from TID bank during inventory */
		if (sing.getStrategy() == Singulation.SingulationStrategy.TID)			
			this.nurApi.setIRConfig(NurApi.IRTYPE_EPCDATA, NurApi.BANK_TID, 0, NurTagProcessor.WORD_COUNT_TID_BANK);
		
		/* else if operation list contains a read TID operation, read secure amount of words from TID bank */
		else if (opListInspResult != null && (opListInspResult.getFlags() & RFUtils.OperationListInspectionResult.LIST_INSPECTION_TID_READ_OPERATION) != 0)			
			this.nurApi.setIRConfig(NurApi.IRTYPE_EPCDATA, NurApi.BANK_TID, 0, NurTagProcessor.WORD_COUNT_TID_BANK);

		/* disable IRConfig */
		else this.nurApi.setIRState(false);
		
		NurInventoryExtended nie = new NurInventoryExtended();		
		nie.transitTime = this.setup.getInventoryTransitTime();
		nie.Q = this.setup.getInventoryQ();
		nie.session = this.setup.getInventorySession();
		nie.rounds = this.setup.getInventoryRounds();
		
		NurInventoryExtendedFilter[] nieFilters = buildFilterArray(filters, nie);
		NurRespInventory inv = nurApi.inventoryExtended(nie, nieFilters, nieFilters.length);
		
		if (inv.numTagsFound > 0) {
			this.nurApi.fetchTags(true);
		}
		NurTagStorage storage = nurApi.getStorage();		
		
		if (log.isLoggable(Level.FINE))			 
			log.log(Level.FINE, "Inventory cycle took {0} ms. Tags found: {1}.", new Object[] { (new Date().getTime() - now), inv.numTagsFound });										

		log.exiting(this.getClass().getName(), "inventory", storage);
		return storage;
	}

	/**
	 * Builds a NUR-compliant filter array from a given {@link List} of
	 * {@link Filter} objects.
	 * 
	 * @param filters
	 *            a {@link List} of {@link Filter} objects.
	 * @return an array of NurInventoryExtendedFilter objects.
	 */
	NurInventoryExtendedFilter[] buildFilterArray(List<Filter> filters, NurInventoryExtended nie) {
		if (log.isLoggable(Level.FINER))
			log.entering(this.getClass().getName(), "buildFilterArray", RFUtils.serializeList(filters, Filter.class));

		List<Filter> effectiveFilters = new ArrayList<Filter>();

		for (Filter filter : filters) {
			List<Filter> subFilters = RFUtils.applyMask(filter);
			if (subFilters != null) effectiveFilters.addAll(subFilters);
		}

		/*
		 * In accordance to table 6.21 of UHF C1G2 1.2.0 standard 
		 * 00 = include all tags 
		 * 01 = include all tags 
		 * 10 = include tags with SL==0 only 
		 * 11 = include tags only SL==1 only
		 */
		nie.inventorySelState = effectiveFilters.size() > 0 ? 0b11 : 0b00;
		
		List<NurInventoryExtendedFilter> nieFilters = new ArrayList<>(effectiveFilters.size());
		for (int iFilter = 0; iFilter < effectiveFilters.size(); iFilter++) {
			Filter filter = effectiveFilters.get(iFilter);

			NurInventoryExtendedFilter nieFilter = new NurInventoryExtendedFilter();
			nieFilter.bank = filter.getBank();
			nieFilter.address = filter.getBitOffset();
			nieFilter.maskdata = filter.getData();
			nieFilter.maskBitLength = filter.getBitLength();
			/*
			 * In accordance to table 6.19 of UHF C1G2 1.2.0 standard 000:
			 * Filter writes Inventoried flag (S0) => SESSION_S0 001: Filter
			 * writes Inventoried flag (S1) => SESSION_S1 010: Filter writes
			 * Inventoried flag (S2) => SESSION_S2 011: Filter writes
			 * Inventoried flag (S3) => SESSION_S3 100: Filter writes SL flag =>
			 * SESSION_SL
			 */
			nieFilter.targetSession = NurApi.SESSION_SL;

			/*
			 * In accordance to table 6.20 of UHF C1G2 1.2.0 standard 000 : set
			 * SL = 1 if filter matches, set SL = 0 otherwise (FILTER_ACTION_0)
			 * 010 : do nothing if filter matches, set SL = 0 otherwise
			 * (FILTER_ACTION_2) 100 : set SL = 0 if filter matches, set SL = 1
			 * otherwise (FILTER_ACTION_4) 101 : set SL = 0 if filter matches,
			 * do nothing otherwise (FILTER_ACTION_5)
			 * 
			 * To implement the filters as boolean AND, the first filter will do
			 * action 000 if inclusive or action 100 if exclusive, all following
			 * filters in the list will do action 010 if inclusive or action 101
			 * if exclusive
			 */

			if (iFilter == 0)
				nieFilter.action = filter.isMatch() ? NurApi.FILTER_ACTION_0 : NurApi.FILTER_ACTION_4;
			else
				nieFilter.action = filter.isMatch() ? NurApi.FILTER_ACTION_2 : NurApi.FILTER_ACTION_5;

			nieFilters.add(nieFilter);
			
		}
		
		/* Add filters to reset the inventoried flag back to A, to work around tags being quiet after inventory. */  
		NurInventoryExtendedFilter resetFilter1 = new NurInventoryExtendedFilter();
		resetFilter1.targetSession = NurApi.SESSION_S0;
		resetFilter1.action = NurApi.FILTER_ACTION_1;
		resetFilter1.bank = NurApi.BANK_EPC;
		resetFilter1.maskdata = new byte[] { };
		
		NurInventoryExtendedFilter resetFilter2 = new NurInventoryExtendedFilter();
		resetFilter2.targetSession = NurApi.SESSION_S0;
		resetFilter2.action = NurApi.FILTER_ACTION_6;
		resetFilter2.bank = NurApi.BANK_EPC;
		resetFilter2.maskdata = new byte[] { };
		
		nieFilters.add(resetFilter1);
		nieFilters.add(resetFilter2);
		
		NurInventoryExtendedFilter[] nieFilterArray = nieFilters.toArray(new NurInventoryExtendedFilter[nieFilters.size()]);
		log.exiting(this.getClass().getName(), "buildFilterArray", nieFilterArray);
		return nieFilterArray;
	}

	/**
	 * Returns the currently selected region as region string.
	 */
	@Override
	public String getRegion() {
		log.entering("{0}.{1} called", this.getClass().getName(), "getRegion");
		String result = this.setup.getRegion().regionCode;
		log.exiting(this.getClass().getName(), "getRegion", result);
		return result;
	}

	/**
	 * Sets the RFID module to the given {@link RfcRegion} and applies the
	 * {@link AntennaConfigurationList}.
	 * 
	 * @param rfcRegion
	 *            the {@link RfcRegion} instance of the new region
	 * @param antennaConfigurationList
	 *            the {@link AntennaConfigurationList} of the configuration
	 * 
	 * @throws ImplementationException
	 *             if setting the TX levels fail.
	 * @throws ParameterException
	 *             if setting the TX levels fail.
	 */
	@Override
	public void setRegion(RFRegion rfcRegion, AntennaConfigurationList antennaConfigurationList) throws ParameterException, ImplementationException {
		if (log.isLoggable(Level.FINER))
			log.entering(this.getClass().getName(), "setRegion", 
				new Object[] { RFUtils.serialize(rfcRegion), RFUtils.serialize(antennaConfigurationList) });

		RegulatoryCapabilities regulatoryCapabilities = rfcRegion.getRegulatoryCapabilities();
		Region region = Region.fromRegionCode(rfcRegion.getId());
		this.setup.setRegion(region);

		for (AntennaConfiguration antennaConfiguration : antennaConfigurationList.getEntryList()) {
			setAntennaConfiguration(antennaConfiguration, regulatoryCapabilities, true);
		}
		
		log.exiting(this.getClass().getName(), "setRegion");
	}

	/**
	 * Applies an antenna configuration using the given regulatory capabilities.
	 * 
	 * @param antennaConfiguration
	 *            an instance of {@link AntennaConfiguration}
	 * @param regulatoryCapabilities
	 *            an instance of {@link RegulatoryCapabilities} containing the
	 *            values referenced by the antenna configuration
	 * @param forceTune
	 *            controls when the antenna is to be tuned. If false, the
	 *            antenna is only tuned if it is added to the antenna list. If
	 *            true, the antenna is tuned either way.
	 * @throws ParameterException
	 *             if writing the configuration to the module setup fails.
	 * @throws ImplementationException
	 *             if writing the configuration to the module setup fails.
	 */
	@Override
	public void setAntennaConfiguration(AntennaConfiguration antennaConfiguration, RegulatoryCapabilities regulatoryCapabilities, boolean forceTune)
			throws ParameterException, ImplementationException {
		if (log.isLoggable(Level.FINER))
			log.entering(this.getClass().getName(), "setAntennaConfiguration", new Object[] { 
				RFUtils.serialize(antennaConfiguration), RFUtils.serialize(regulatoryCapabilities) });

		TransmitPowerTable transmitPowerTable = regulatoryCapabilities.getTransmitPowerTable();
		TransmitPowerTableEntry transmitPowerTableEntry = transmitPowerTable.getEntryList().get(antennaConfiguration.getTransmitPower());

		Antenna antenna = Antenna.fromId(antennaConfiguration.getId());
		TxLevel txLevel = TxLevel.fromDBm(transmitPowerTableEntry.getTransmitPower());
		this.setup.setTxLevel(antenna, txLevel);

		ConnectType connect = antennaConfiguration.getConnect();

		if (connect == null)
			connect = ConnectType.AUTO;

		switch (connect) {
			case TRUE:
				setConnected(antenna, true, forceTune);
				break;
			case FALSE:
				setConnected(antenna, false, forceTune);
				break;
			default:
				boolean conState = this.setup.autoDetect(antennaConfiguration.getId());
				setConnected(antenna, conState, forceTune);
				break;
		}
		
		log.exiting(this.getClass().getName(), "setAntennaConfiguration");
	}

	private void setConnected(Antenna antenna, boolean connected, boolean forceTune) throws ImplementationException {
		log.entering(this.getClass().getName(), "setConnected", new Object[] { antenna, connected, forceTune });
		if (connected) {
			if (!connectedAntennas.contains(antenna.id)) {
				connectedAntennas.add(antenna.id);
				setup.setAntennaState(antenna, true);
				if (!forceTune)
					tuneAntenna(antenna.id);
			}
			if (forceTune)
				tuneAntenna(antenna.id);

		} else {
			if (connectedAntennas.contains(antenna.id)) {
				connectedAntennas.remove((Short) antenna.id);
				setup.setAntennaState(antenna, false);
			}
		}
		
		log.exiting(this.getClass().getName(), "setConnected");
	}

	/**
	 * Returns the antenna properties for the given number of antennas.
	 * 
	 * @param numOfAntennas
	 *            the number of antennas the module supports
	 * @return an {@link AntennaPropertyList} instance
	 * 
	 * @throws ImplementationException
	 *             if detection of the antenna connection state fails.
	 */
	@Override
	public AntennaPropertyList getAntennaProperties(Map<Short, ConnectType> connectTypeMap) throws ImplementationException {
		log.entering(this.getClass().getName(), "getAntennaProperties", connectTypeMap);

		AntennaPropertyList result = new AntennaPropertyList();
		for (short i = 0; i < connectTypeMap.size(); i++) {

			short antennaId = (short) (i + 1);
			ConnectType conType = connectTypeMap.get(antennaId);
			boolean conState = false;

			if (conType == null) conType = ConnectType.AUTO;

			switch (conType) {
				case TRUE:
					conState = true;
					break;
				case FALSE:
					conState = false;
					break;
				default:
					conState = this.setup.autoDetect(antennaId);
					break;
			}
		
			AntennaProperties ap = new AntennaProperties();
			ap.setId(antennaId);
			ap.setConnected(conState);
			ap.setGain((short) 0); /* impossible to detect gain */
			result.getEntryList().add(ap);
		}
		
		if (log.isLoggable(Level.FINER))
			log.exiting(this.getClass().getName(), "getAntennaProperties", RFUtils.serialize(result));

		return result;
	}

	private void tuneAntenna(short id) {
		log.entering(this.getClass().getName(), "tuneAntenna", id);

		try {
			Region reg = this.setup.getRegion();
			NurTuneResponse[] tuneRes = null;

			switch (reg) {
				case RegionEU:
					tuneRes = nurApi.tuneEUBand(Antenna.fromId(id).nurApiAntId, true);
					log.log(Level.INFO, "Antenna {0} tuned for EU band with result: {1} dBm", new Object[] { id, tuneRes[0].dBm });
					break;
				case RegionFCC:
					tuneRes = nurApi.tuneFCCBands(Antenna.fromId(id).nurApiAntId, true);
					log.log(Level.INFO,"Antenna {0} tuned for FCC band with result: {1} dBm", new Object[] { id, tuneRes[0].dBm });
					break;
				default:
					log.log(Level.INFO,"Tuning of antenna {0} unchanged due to unspecified or unsupported region.", id);
			}
		} catch (Exception e) {			
			LogRecord logRec = new LogRecord(Level.SEVERE, "Failed tuning antenna {0}: {1}");
			logRec.setThrown(e);
			logRec.setParameters(new Object[] { id, e });
			logRec.setLoggerName(log.getName());
			log.log(logRec);					
		}
	}

	@Override
	public String getFirmwareVersion() throws ImplementationException {
		log.entering(getClass().getName(), "getFirmwareVersion");
		try {			
			NurRespReaderInfo info = this.nurApi.getReaderInfo();
			String nurFwVersion = info.swVersion;
			String result = "UNKNOWN";
			switch (nurFwVersion) {
				case "5.16-A":
					result = "2.6"; break;
				case "5.10-A":
					result = "1.7"; break;	
				case "5.5-A":
					result = "1.5"; break;	
				case "5.4-A":
					result = "1.1"; break;
				case "4.8-A":
					result = "1.0"; break;				
			}			
			
			log.exiting(getClass().getName(), "getFirmwareVersion", result);
			return result;

		} catch (Exception ex) {
			throw new ImplementationException("Failed to retrieve firmware version.", ex);
		}
	}

	@Override
	public synchronized void installFirmware() throws ImplementationException {
		try {
			log.info("Closing connection to RFID module.");
			this.closeConnection();
			log.info("Running firmware installation script.");
			File scriptFile = new File(Environment.FIRMWARE_UPDATE_SCRIPT);
			new FirmwareUpdater(scriptFile).execute();					
		} catch (ExecutionException | ConnectionException e) {
			throw new ImplementationException(e);
		}
		finally {
			log.info("Re-establishing connection to RFID module.");	
			try { this.openConnection(); }
			catch (ConnectionException ex) { throw new ImplementationException(ex); }
		}
	}

	@Override
	public RssiFilter getRssiFilter() {		
		RssiFilter rssi = new RssiFilter();
		rssi.setMinRssi(this.setup.getMinInventoryRssi());
		rssi.setMaxRssi(this.setup.getMaxInventoryRssi());
		return rssi;
	}

	@Override
	public SingulationControl getSingulationControl() {
		SingulationControl sc = new SingulationControl();
		sc.setQValue(this.setup.getInventoryQ());
		sc.setRounds(this.setup.getInventoryRounds());
		sc.setSession(this.setup.getInventorySession());	
		sc.setTransitTime((short) this.setup.getInventoryTransitTime());
		return sc;
	}

	@Override
	public void setRssiFilter(RssiFilter rssiFilter) throws ImplementationException {
		this.setup.setRssiFilter(rssiFilter);
	}

	@Override
	public void setSingulationControl(SingulationControl singulation) throws ImplementationException {
		this.setup.setSingulationControl(singulation);
	}

	@Override
	public int getMaxAntennas() throws ImplementationException {
		NurGPIOConfig[] configs = null;
		int ret = 1;
		
		/* if exception occurs during acquisition of GPIO config, module seems 
		 * not no have GPIOs, thus only one antenna can be present  */
		try { configs = this.nurApi.getGPIOConfigure(); }
		catch (Exception ex) { return ret; }
		
		/* the same applies to the case that GPIO config is null or empty. */
		if (configs == null || configs.length == 0)
			return ret;
		
		/* if GPIO config has at least one entry */
		if (configs.length >= 1) {
			/* if type of first entry is set to antenna control, module has at least two antennas */
			if (configs[0].type == NurGPIOConfig.GPIO_TYPE_ANTCTL1 || 
				configs[0].type == NurGPIOConfig.GPIO_TYPE_ANTCTL2)
				ret = 2;
			
			/* if GPIO config has at least two entries */
			if (configs.length >= 2)
				/* if type of second entry is set to antenna control, module has four antennas */
				if (configs[1].type == NurGPIOConfig.GPIO_TYPE_ANTCTL1 || 
					configs[1].type == NurGPIOConfig.GPIO_TYPE_ANTCTL2)
					ret = 4;		
		}
		
		return ret;
	}	
}
