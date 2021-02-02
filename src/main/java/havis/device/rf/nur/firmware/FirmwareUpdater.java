package havis.device.rf.nur.firmware;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FirmwareUpdater {
	private static final Logger log = Logger.getLogger(FirmwareUpdater.class.getName());

	File scriptFile;

	public FirmwareUpdater(File scriptFile) {
		super();
		
		this.scriptFile = scriptFile;				
	}

	public int execute() throws ExecutionException {
		String command = this.scriptFile.getAbsolutePath();		
		log.log(Level.FINE, "Executing command: {0}", command);
		
		int resultCode;
		try {
			resultCode = Runtime.getRuntime().exec(command, new String[] {}, null).waitFor();
		} catch (Exception e) {
			throw new ExecutionException(e);
		}
		
		log.log(Level.FINE, "Command executed with result code: {0}", resultCode);		
		return resultCode;			
	}
}
