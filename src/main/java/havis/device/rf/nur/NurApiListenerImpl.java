package havis.device.rf.nur;

import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.nordicid.nurapi.NurApiListener;
import com.nordicid.nurapi.NurEventAutotune;
import com.nordicid.nurapi.NurEventClientInfo;
import com.nordicid.nurapi.NurEventDeviceInfo;
import com.nordicid.nurapi.NurEventEpcEnum;
import com.nordicid.nurapi.NurEventFrequencyHop;
import com.nordicid.nurapi.NurEventIOChange;
import com.nordicid.nurapi.NurEventInventory;
import com.nordicid.nurapi.NurEventNxpAlarm;
import com.nordicid.nurapi.NurEventProgrammingProgress;
import com.nordicid.nurapi.NurEventTagTrackingChange;
import com.nordicid.nurapi.NurEventTagTrackingData;
import com.nordicid.nurapi.NurEventTraceTag;
import com.nordicid.nurapi.NurEventTriggeredRead;

public class NurApiListenerImpl implements NurApiListener {
	
	Logger log = Logger.getLogger(NurApiListenerImpl.class.getName());
	
	private Semaphore semaphore = new Semaphore(0);
	
	public void waitForDisconnectedEvent() throws InterruptedException {
		semaphore.acquire();
	}
	
	/**
	 * Event-handler for the disconnected event. Used to release the semaphore
	 * blocking any access while disconnect is in progress.
	 */
	@Override
	public void disconnectedEvent() {
		log.log(Level.FINER, "{0}.{1} called", new Object[] { this.getClass().getName(), "disconnectedEvent" });		
		semaphore.release();
	}
	
	@Override
	public void connectedEvent() {
	}

	@Override
	public void IOChangeEvent(NurEventIOChange arg0) {
	}

	@Override
	public void bootEvent(String arg0) {
		
	}

	@Override
	public void clientConnectedEvent(NurEventClientInfo arg0) {
	}

	@Override
	public void clientDisconnectedEvent(NurEventClientInfo arg0) {
	}

	@Override
	public void debugMessageEvent(String arg0) {
	}

	@Override
	public void deviceSearchEvent(NurEventDeviceInfo arg0) {
	}

	@Override
	public void epcEnumEvent(NurEventEpcEnum arg0) {
	}

	@Override
	public void frequencyHopEvent(NurEventFrequencyHop arg0) {
	}

	@Override
	public void inventoryExtendedStreamEvent(NurEventInventory arg0) {
	}

	@Override
	public void inventoryStreamEvent(NurEventInventory arg0) {
	}

	@Override
	public void logEvent(int arg0, String arg1) {
	}

	@Override
	public void nxpEasAlarmEvent(NurEventNxpAlarm arg0) {
	}

	@Override
	public void programmingProgressEvent(NurEventProgrammingProgress arg0) {
	}

	@Override
	public void traceTagEvent(NurEventTraceTag arg0) {
	}

	@Override
	public void triggeredReadEvent(NurEventTriggeredRead arg0) {
	}

	@Override
	public void autotuneEvent(NurEventAutotune arg0) {
	}

	@Override
	public void tagTrackingChangeEvent(NurEventTagTrackingChange arg0) {
		
	}

	@Override
	public void tagTrackingScanEvent(NurEventTagTrackingData arg0) {
	
	}

}
