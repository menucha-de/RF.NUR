package havis.device.rf.nur;

import havis.device.rf.common.util.RFUtils;
import havis.device.rf.configuration.RssiFilter;
import havis.device.rf.configuration.SingulationControl;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.nur.Constants.Antenna;
import havis.device.rf.nur.Constants.LinkFrequency;
import havis.device.rf.nur.Constants.Region;
import havis.device.rf.nur.Constants.TxLevel;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.nordicid.nurapi.NurApi;
import com.nordicid.nurapi.NurRespReaderInfo;
import com.nordicid.nurapi.NurSetup;
import com.nordicid.nurapi.ReflectedPower;

/**
 * This class serves as an interface to the NUR specific module setup. It
 * provides several convenience methods making it unnecessary to work with the
 * NurSetup object directly.
 * 
 */

public class NurConfigurationHelper {

	private final static Logger log = Logger.getLogger(NurConfigurationHelper.class.getName());

	private NurApi nurApi;
	private NurSetup nurSetup;
	private NurRespReaderInfo moduleInfo;

	private int prevSetupAntennaMask = 0;
	private int prevParamAntennaMask = 0;

	private int inventoryTransitTime;

	private Region region = Region.RegionUnspecified;

	/**
	 * Creates an instance of this class
	 * 
	 * @param nurApi
	 *            a NurApi instance
	 */
	protected NurConfigurationHelper(NurApi nurApi) {
		super();
		this.nurApi = nurApi;
		log.finer("NurConfigurationHelper instanciated.");
	}

	/**
	 * Loads the module setup and the modules reader info.
	 * 
	 * @throws ImplementationException
	 *             if one of the above fails.
	 */
	protected void loadModuleSetup() throws ImplementationException {
		try {
			this.nurSetup = nurApi.getModuleSetup();
			
			if (this.nurSetup.antennaMask == NurApi.ANTENNAMASK_4)
				this.nurSetup.antennaMask = 0;
			
			this.moduleInfo = nurApi.getReaderInfo();
		} catch (Exception e) {
			throw new ImplementationException(e);
		}
	}

	/**
	 * Saves the complete module setup.
	 * 
	 * @throws ImplementationException
	 *             if saving the module setup fails.
	 */
	protected void saveModuleSetup() throws ImplementationException {
		this.saveModuleSetup(NurApi.SETUP_ALL);
	}

	/**
	 * Saves the module setup in the modules memory (RAM) either entirely or
	 * only the part specified by the flags parameter.
	 * 
	 * @param flags
	 *            One of the NurApi.SETUP_* constants (e.g. SETUP_ALL,
	 *            SETUP_ANTMASK, SETUP_TXLEVEL);
	 * @throws ImplementationException
	 *             if saving the module setup fails.
	 */
	protected void saveModuleSetup(int flags) throws ImplementationException {
		
		if (log.isLoggable(Level.FINER))			
			log.entering(getClass().getName(), "saveModuleSetup", setupFlagsToString(flags));
		
		if (nurSetup.antennaMask == 0) 
			nurSetup.antennaMask = NurApi.ANTENNAMASK_4;
		
		try {
			this.nurApi.setModuleSetup(this.nurSetup, flags);
			
			if ((flags & NurApi.SETUP_REGION) > 0) 
				this.nurApi.storeSetup(NurApi.SETUP_REGION);
			
			
		} catch (Exception e) {
			throw new ImplementationException(e);
			
		} finally {
			if (nurSetup.antennaMask == NurApi.ANTENNAMASK_4) 
				nurSetup.antennaMask = 0;
		}
		
		log.exiting(getClass().getName(), "saveModuleSetup");
	}

	private String setupFlagsToString(int flags) {
		String flagStr = "";
		if (flags == NurApi.SETUP_ALL)
			flagStr = "ALL";
		else {
			if ((flags & NurApi.SETUP_ANTMASK) > 0)
				flagStr += "ANTMASK ";
			if ((flags & NurApi.SETUP_PERANTPOWER) > 0)
				flagStr += "PERANTPOWER ";
			if ((flags & NurApi.SETUP_TXLEVEL) > 0)
				flagStr += "TXLEVEL ";
			if ((flags & NurApi.SETUP_REGION) > 0)
				flagStr += "REGION ";
			if ((flags & NurApi.SETUP_LINKFREQ) > 0)
				flagStr += "LINKFREQ ";			
			if ((flags & NurApi.SETUP_INVRSSIFILTER) > 0)
				flagStr += "INVRSSIFILTER ";			
			if ((flags & NurApi.SETUP_INVQ) > 0)
				flagStr += "INVQ ";			
			if ((flags & NurApi.SETUP_INVSESSION) > 0)
				flagStr += "INVSESSION ";			
			if ((flags & NurApi.SETUP_INVROUNDS) > 0)
				flagStr += "INVROUNDS ";
		}		
		return flagStr.trim();
	}
		
	/**
	 * Returns the enabled / disabled state of a specific antenna. The state is
	 * determined based on the antenna mask and has nothing to do with the
	 * connection state of the antenna.
	 * 
	 * @param antenna
	 *            an {@link Antenna} instance.
	 * @return true if the antenna is enabled, false otherwise.
	 */
	protected boolean getAntennaState(Antenna antenna) {
		log.entering(getClass().getName(), "getAntennaState", antenna );
		boolean res = (this.nurSetup.antennaMask & antenna.nurApiAntMask) != 0;		
		log.exiting(getClass().getName(), "getAntennaState", res );
		return res;
	}

	/**
	 * Sets the enabled / disabled state of a specific antenna. The changed
	 * state is written to the module.
	 * 
	 * @param antenna
	 *            an {@link Antenna} instance
	 * @param enabled
	 *            specifies if antenna is to be enabled or not.
	 * @throws ImplementationException
	 *             if saving the module setup fails.
	 */
	protected void setAntennaState(Antenna antenna, boolean enabled)
			throws ImplementationException {
		log.entering(getClass().getName(), "setAntennaState", new Object[] { antenna, enabled });

		int oldVal = this.nurSetup.antennaMask;
		int newVal = 0;

		if (enabled)
			newVal = oldVal | antenna.nurApiAntMask;
		else
			newVal = oldVal & ~antenna.nurApiAntMask;

		if (oldVal != newVal) {
			this.nurSetup.antennaMask = newVal;
			log.log(Level.FINER, "Changed antenna mask from {0} to {1}", new Object[] { oldVal, newVal });
			try {
				this.saveModuleSetup(NurApi.SETUP_ANTMASK);
			} catch (Exception e) {
				this.nurSetup.antennaMask = oldVal;
				throw new ImplementationException(
						String.format("Failed to apply antenna state to value %b for antenna: %s", enabled, antenna), e);
			}
		}
		log.exiting(getClass().getName(), "setAntennaState");
	}

	/**
	 * Returns the number of enabled antennas based on the antenna mask stored
	 * on the module.
	 * 
	 * @return the number of enabled antennas.
	 */
	private short getNumberOfEnabledAntennas() {
		short cnt = 0;

		if (getAntennaState(Antenna.Antenna1))
			cnt++;
		if (getAntennaState(Antenna.Antenna2))
			cnt++;		

		return cnt;
	}
	
	/**
	 * Sets the module's antenna states based on a list of antenna IDs and
	 * returns the count of actually enabled antennas. This method can only
	 * enable antennas that are not explcitly disabled via configuration and for
	 * the sake of performance this detects if the specified list of antennas
	 * leads to a change that has to be saved on the module. Before the antenna
	 * mask is changed on the module its state is stored an can be reverted by
	 * calling revertAntennaMask. This however must not be done manually since
	 * this method automatically reverts the antenna mask prior to changing it
	 * if needed.
	 * 
	 * @param antennas
	 *            a list of antenna IDs
	 * @return the effective count of enabled antennas. If the is 0 the caller
	 *         can and should cancel further operations.
	 * @throws ImplementationException
	 *             if saving the module setup fails.
	 */
	protected short applyAntennaSelection(List<Short> antennas) throws ImplementationException {
		
		log.entering(getClass().getName(), "applyAntennaSelection", RFUtils.serializeList(antennas, Short.class));
		
		/* calculate an antenna mask from the antennas list passed */
		int antennaParamMask = calculateAntennaMask(antennas);

		/* if antenna list differs from the one previously passed, revert setup mask to original state */
		if (prevParamAntennaMask != antennaParamMask) {
			if (prevParamAntennaMask != 0)
				this.revertAntennaMask();
			prevParamAntennaMask = antennaParamMask;
		}

		/* calculate the new setup antenna mask by ANDing the current setup mask with the antenna list mask */
		int newSetupAntennaMask = this.nurSetup.antennaMask & antennaParamMask;

		/* if the newly calculated antenna mask equals the one already set on the module, return */
		if (newSetupAntennaMask == this.nurSetup.antennaMask) {
			short res = getNumberOfEnabledAntennas();
			log.exiting(getClass().getName(), "applyAntennaSelection", res);
			return res;
		}

		/* if the newly calculated antenna mask equals 0, which means, that all antennas are off, return 0 */
		if (newSetupAntennaMask == 0) {
			log.exiting(getClass().getName(), "applyAntennaSelection", 0);
			return 0;		
		}

		/* otherwise save the currently set setup antenna mask (for later restore) */
		this.prevSetupAntennaMask = this.nurSetup.antennaMask;

		/* write the new setup mask to the module */
		this.nurSetup.antennaMask = newSetupAntennaMask;
		this.saveModuleSetup(NurApi.SETUP_ANTMASK);

		short res = getNumberOfEnabledAntennas();
		log.exiting(getClass().getName(), "applyAntennaSelection", res);

		return res;
	}

	/**
	 * Reverts the antenna mask to its previous state. Prior to applying an
	 * antenna selection the method applyAntennaSelection uses this method to
	 * restore the previous (predefined) antenna state.
	 * 
	 * @throws ImplementationException
	 *             if saving the module setup fails.
	 */
	private void revertAntennaMask() throws ImplementationException {
		if (this.prevSetupAntennaMask == 0 || this.nurSetup.antennaMask == this.prevSetupAntennaMask)
			return;

		log.log(Level.FINER, "Reverting antenna mask from {0} to {1}.", 
				new Object[] { this.nurSetup.antennaMask, prevParamAntennaMask });

		this.nurSetup.antennaMask = prevSetupAntennaMask;
		this.saveModuleSetup(NurApi.SETUP_ANTMASK);
	}

	/**
	 * Calculates an antenna mask based on a list of antenna IDs. If this list
	 * contains an ID 0 the mask NurApi.ANTENNAMASK_1 | NurApi.ANTENNAMASK_2 is returned.
	 * 
	 * @param antennas
	 *            a list of antenna IDs (optionally containing an ID 0 for all, i.e. antenna 1 and 2)
	 * @return an antenna mask having the bits set to 1 that correspond to the
	 *         antenna IDs in the list.
	 */
	private int calculateAntennaMask(List<Short> antennas) {
		if (log.isLoggable(Level.FINER))		
			log.entering(getClass().getName(), "calculateAntennaMask", RFUtils.serializeList(antennas, Short.class));
		
		int antennaMask = 0;
		loop: for (Short antenna : antennas) {			
			switch (antenna) {
				case 0:
					antennaMask = NurApi.ANTENNAMASK_1 | NurApi.ANTENNAMASK_2;
					break loop; 
					
				case 1:
					antennaMask |= NurApi.ANTENNAMASK_1;
					break;
				
				case 2:
					antennaMask |= NurApi.ANTENNAMASK_2;
					break;
			}
		}
		log.exiting(getClass().getName(), "calculateAntennaMask", antennaMask);
		
		return antennaMask;
	}

	/**
	 * Returns the antenna connection state for a given antenna.
	 * 
	 * @param antenna
	 *            an antenna ID
	 * @return a true if the antenna is connected and false otherwise.
	 * @throws ImplementationException
	 *             if something goes wrong during process of detecting antenna connection states.
	 */
	protected boolean autoDetect(short antenna) throws ImplementationException {		
		log.entering(getClass().getName(), "autoDetect", antenna);
		int prevSetupAntennaMask = this.nurSetup.antennaMask;

		boolean result = false;
		
		try {
			if (getRegion() == Region.RegionUnspecified)
				result = false;		
			else {
			
				int antMask = Antenna.fromId(antenna).nurApiAntMask;
				this.nurSetup.antennaMask = antMask;
				this.saveModuleSetup(NurApi.SETUP_ANTMASK);
				
				ReflectedPower reflPower = nurApi.getReflectedPower();
	
				double rf = Math.sqrt((double) (reflPower.iPart
						* reflPower.iPart + reflPower.qPart * reflPower.qPart));
				rf /= ((double) reflPower.divider);
				rf = Math.log10(rf) * 20.0;
				if (Double.isInfinite(rf))
					rf = -30;
				
				result = rf < 0;
			}			

		} catch (Exception ex) { 
			throw new ImplementationException(ex); 			
		} finally {
			if (this.nurSetup.antennaMask != prevSetupAntennaMask) {
				this.nurSetup.antennaMask = prevSetupAntennaMask;
				this.saveModuleSetup(NurApi.SETUP_ANTMASK);
			}
		}
		log.exiting(getClass().getName(), "autoDetect", result);		
		return result;
	}
	
	/**
	 * Returns the currently selected region of the module.
	 * 
	 * @return a {@link Region} instance.
	 */
	protected Region getRegion() {
		return this.region;
	}

	/**
	 * Changes the selected region of the module and saves the changed module
	 * setup.
	 * 
	 * @param region
	 *            a {@link Region} instance
	 * @throws ImplementationException
	 *             if saving the module setup fails.
	 */
	protected void setRegion(Region region) throws ImplementationException {
		log.entering(getClass().getName(), "setRegion", region);

		Region oldRegion = this.region;
		this.region = region;

		if (region == Region.RegionUnspecified) {
			log.finer("Region setting is now 'unspecified'. Module will not perform any operations anymore.");
		} else {
			int oldVal = oldRegion == Region.RegionUnspecified ? oldRegion.nurApiRegion : nurSetup.regionId;
			int newVal = region.nurApiRegion;

			if (oldVal != newVal) {
				log.log(Level.FINER, "Changed region from {0} to {1}", new Object[] { oldVal, newVal });
				this.nurSetup.regionId = newVal;
				try {
					this.saveModuleSetup(NurApi.SETUP_REGION);
				} catch (Exception e) {
					this.region = oldRegion;
					this.nurSetup.regionId = oldVal;
					throw new ImplementationException(String.format("Failed to apply region: %s", region), e);
				}
			}
		}
		log.exiting(getClass().getName(), "setRegion");
	}

	/**
	 * Returns the default TX level of the module, that is the one TX level to
	 * be used when the TX level of a specific antenna is set to {@link TxLevel}
	 * .TxLevelDefault
	 * 
	 * @return a {@link TxLevel} instance
	 */
	protected TxLevel getDefaultTxLevel() {
		TxLevel res = TxLevel.fromNurConstant(this.nurSetup.txLevel);
		return res;
	}

	/**
	 * Sets the default TX level of the module, that is the one TX level to be
	 * used when the TX level of a specific antenna is set to {@link TxLevel}
	 * .TxLevelDefault
	 * 
	 * @param txLevel
	 *            a {@link TxLevel} instance
	 * @throws ParameterException
	 *             if the TX level specified is not valid, e.g. {@link TxLevel}
	 *             .TxLevelDefault.
	 * @throws ImplementationException
	 *             if saving the module setup fails.
	 */
	protected void setDefaultTxLevel(TxLevel txLevel) throws ParameterException, ImplementationException {		
		log.entering(getClass().getName(), "setDefaultTxLevel", txLevel);

		if (txLevel == TxLevel.TxLevelDefault)
			throw new ParameterException(String.format(
					"%s is no valid parameter for this function.", txLevel));

		int oldVal = this.nurSetup.txLevel;
		int newVal = txLevel.nurApiConstant;

		if (oldVal != newVal) {
			log.log(Level.FINER, "Changed TX level from {0} to {1}", new Object[] { oldVal, newVal });		
			this.nurSetup.txLevel = newVal;
			try {
				this.saveModuleSetup(NurApi.SETUP_TXLEVEL);
			} catch (Exception e) {
				this.nurSetup.txLevel = oldVal;
				throw new ImplementationException(String.format(
						"Failed to apply TX level: %s", txLevel), e);
			}
		}
		
		log.exiting(getClass().getName(), "setDefaultTxLevel");
	}

	/**
	 * Gets the TX level of the specified antenna.
	 * 
	 * @param antenna
	 *            an {@link Antenna} instance.
	 * @return a {@link TxLevel} instance
	 * @throws ParameterException
	 *             if no power information is available for the specified
	 *             antenna
	 */
	protected TxLevel getTxLevel(Antenna antenna) throws ParameterException {
		
		log.entering(getClass().getName(), "getTxLevel", antenna);
		
		if (nurSetup.antPower.length < antenna.nurApiAntId + 1)
			throw new ParameterException(String.format(
					"No power information found for antenna %s.", antenna));

		TxLevel res = TxLevel
				.fromNurConstant(nurSetup.antPower[antenna.nurApiAntId]);
		
		log.exiting(getClass().getName(), "getTxLevel", res);
		
		return res;
	}

	/**
	 * Sets the TX level for a specific antenna.
	 * 
	 * @param antenna
	 *            an {@link Antenna} instance
	 * @param txLevel
	 *            a {@link TxLevel} instance
	 * @throws ParameterException
	 *             if no power information is available for the specified
	 *             antenna
	 * @throws ImplementationException
	 *             if saving the module setup fails
	 */
	protected void setTxLevel(Antenna antenna, TxLevel txLevel)
			throws ParameterException, ImplementationException {
		log.entering(getClass().getName(), "setTxLevel", new Object[] { antenna, txLevel });

		if (this.nurSetup.antPower.length < antenna.nurApiAntId + 1)
			throw new ParameterException(String.format(
					"No power information found for the antenna %s.", antenna));

		int[] oldVal = this.nurSetup.antPower;
		int[] newVal = Arrays.copyOf(oldVal, oldVal.length);

		if (antenna == Antenna.Auto)
			for (int iAntenna = 0; iAntenna < this.nurSetup.antPower.length; iAntenna++)
				newVal[iAntenna] = txLevel.nurApiConstant;
		else
			newVal[antenna.nurApiAntId] = txLevel.nurApiConstant;

		if (!Arrays.equals(oldVal, newVal)) {
			this.nurSetup.antPower = newVal;			
			if (log.isLoggable(Level.FINER))
				log.log(Level.FINER, "Changed TX level from {0} to {1}", 
					new Object[] { RFUtils.serialize(oldVal), RFUtils.serialize(newVal) });			
			try {
				this.saveModuleSetup(NurApi.SETUP_PERANTPOWER);
			} catch (Exception e) {
				this.nurSetup.antPower = oldVal;
				throw new ImplementationException(String.format(
						"Failed to apply antenna power %s to antenna %s.", txLevel,
						antenna), e);
			}
		}
		
		log.exiting(getClass().getName(), "setTxLevel");
	}
		
	
	/**	 
	 * Returns the module's link frequency.
	 * 
	 * @return a {@link LinkFrequency} instance.
	 */
	protected LinkFrequency getLinkFrequency() {
		LinkFrequency res = LinkFrequency.fromNurConst(this.nurSetup.linkFreq);
		return res;
	}

	/**
	 * Sets the module's link frequency.
	 * 
	 * @param linkFreq
	 *            a {@link LinkFrequency} instance
	 * @throws ImplementationException
	 *             if saving the module setup fails.
	 */
	protected void setLinkFrequency(LinkFrequency linkFreq)
			throws ImplementationException {
		log.entering(getClass().getName(), "setLinkFrequency", new Object[] { linkFreq });

		int oldVal = this.nurSetup.linkFreq;
		int newVal = linkFreq.nurApiConstant;

		if (oldVal != newVal) {
			this.nurSetup.linkFreq = linkFreq.nurApiConstant;
			log.log(Level.FINER, "Changed link frequency from {0} to {1}", new Object[] { oldVal, newVal });
			try {
				this.saveModuleSetup(NurApi.SETUP_LINKFREQ);
			} catch (Exception e) {
				this.nurSetup.linkFreq = oldVal;
				throw new ImplementationException(String.format(
						"Failed to apply link frequency: %s", linkFreq), e);
			}
		}
		
		log.exiting(getClass().getName(), "setLinkFrequency");
	}

	/**
	 * Returns the module's firmware version.
	 * 
	 * @return the module's firmware version.
	 */
	protected String getFirmware() {
		return this.moduleInfo.swVersion;
	}

	/**
	 * Returns the module's product name.
	 * 
	 * @return the module's product name.
	 */
	protected String getModuleName() {
		return this.moduleInfo.name;
	}

	/**
	 * Returns the module's serial number.
	 * 
	 * @return the module's serial number.
	 */
	protected String getSerialNumber() {
		return this.moduleInfo.serial;
	}
	
	protected byte getMinInventoryRssi() {
		return (byte)this.nurSetup.inventoryRssiFilter.min;
	}
	
	protected byte getMaxInventoryRssi() {
		return (byte)this.nurSetup.inventoryRssiFilter.max;
	}
	
	protected short getInventoryQ() {
		return (short)this.nurSetup.inventoryQ;
	}
	
	protected short getInventorySession() {
		return (short)this.nurSetup.inventorySession;
	}
	
	protected short getInventoryRounds() {
		return (short)this.nurSetup.inventoryRounds;
	}

	protected void setRssiFilter(RssiFilter rssiFilter) throws ImplementationException {
		com.nordicid.nurapi.RssiFilter oldRssi = this.nurSetup.inventoryRssiFilter; 
		this.nurSetup.inventoryRssiFilter = new com.nordicid.nurapi.RssiFilter(rssiFilter.getMinRssi(), rssiFilter.getMaxRssi());
		try {
			this.saveModuleSetup(NurApi.SETUP_INVRSSIFILTER);
		} catch (ImplementationException e) {
			this.nurSetup.inventoryRssiFilter = oldRssi;
			throw e;
		}		
	}

	protected void setSingulationControl(SingulationControl singulation) throws ImplementationException {
		int oldQ = this.nurSetup.inventoryQ;
		int oldRounds = this.nurSetup.inventoryRounds;
		int oldSession = this.nurSetup.inventorySession;
		int oldTransitTime = this.inventoryTransitTime;
		
		this.nurSetup.inventoryQ = singulation.getQValue();
		this.nurSetup.inventoryRounds = singulation.getRounds();
		this.nurSetup.inventorySession = singulation.getSession();
		this.inventoryTransitTime = singulation.getTransitTime();
		
		try {
			this.saveModuleSetup(NurApi.SETUP_INVQ | NurApi.SETUP_INVROUNDS | NurApi.SETUP_INVSESSION);
		} catch (ImplementationException e) {
			
			this.nurSetup.inventoryQ = oldQ;
			this.nurSetup.inventoryRounds = oldRounds;
			this.nurSetup.inventorySession = oldSession;
			this.inventoryTransitTime = oldTransitTime;
			
			throw e;
		}
	}

	public int getInventoryTransitTime() {
		return inventoryTransitTime;
	}		
}
