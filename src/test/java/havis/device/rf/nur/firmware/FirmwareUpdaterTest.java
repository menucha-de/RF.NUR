package havis.device.rf.nur.firmware;

import static mockit.Deencapsulation.getField;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;

import mockit.Mock;
import mockit.MockUp;

import org.junit.Test;

public class FirmwareUpdaterTest {

	@Test
	public void testFirmwareUpdater() {
		final File scriptFile = new File("/tmp/update.sh");
		FirmwareUpdater fwUpd = new FirmwareUpdater(scriptFile);
		assertEquals(scriptFile, getField(fwUpd, "scriptFile"));
	}

	@Test
	public void testExecute() {
		
		class TestParams { 
			boolean throwExecException;
		}
		final TestParams testParams = new TestParams();
		
		final Runtime runtime = Runtime.getRuntime();		
		
		final Process process = new MockUp<Process>() {
			@Mock
			int waitFor() throws InterruptedException {
				if (testParams.throwExecException)
					throw new InterruptedException();
				return 42; 
			}
		}.getMockInstance();
		
		new MockUp<Runtime>(runtime) {
			@Mock
			Process exec(String command, String[] envp, File dir) {
				return process;
			}
		};
		
		FirmwareUpdater fwUpd = new FirmwareUpdater(new File("/tmp/update.sh"));
		try {
			assertEquals(42, fwUpd.execute());
		} catch (ExecutionException e) {
			fail("Unexpected exception.");
		}
		
		testParams.throwExecException = true;
		try {
			fwUpd.execute();
			fail ("Exception expected.");
		} catch (ExecutionException e) {			
		
		}
				
	}

}
