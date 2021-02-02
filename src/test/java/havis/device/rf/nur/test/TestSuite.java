package havis.device.rf.nur.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/*
 * Call with VM args: 
 * -Djmockit-coverage-excludes=havis\.device\.rf\.nur\.test\..*	
 * to exclude "test" subpackage from coverage report  
 */

@SuiteClasses({ 
	havis.device.rf.nur.firmware.ExecutionExceptionTest.class, 
	havis.device.rf.nur.firmware.FirmwareUpdaterTest.class,
	havis.device.rf.nur.ConstantsTest.class,	
	havis.device.rf.nur.NurConfigurationHelperTest.class,
	havis.device.rf.nur.NurErrorMapTest.class, 
	havis.device.rf.nur.NurHardwareManagerTest.class, 
	havis.device.rf.nur.NurTagProcessorTest.class })

@RunWith(Suite.class)
public class TestSuite {

}
