package havis.device.rf.nur.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import havis.device.rf.RFConsumer;
import havis.device.rf.common.CommunicationHandler;
import havis.device.rf.common.util.RFUtils;
import havis.device.rf.configuration.AntennaConfiguration;
import havis.device.rf.configuration.AntennaProperties;
import havis.device.rf.configuration.Configuration;
import havis.device.rf.configuration.ConfigurationType;
import havis.device.rf.configuration.ConnectType;
import havis.device.rf.exception.CommunicationException;
import havis.device.rf.exception.ConnectionException;
import havis.device.rf.exception.ImplementationException;
import havis.device.rf.exception.ParameterException;
import havis.device.rf.tag.Filter;
import havis.device.rf.tag.TagData;
import havis.device.rf.tag.operation.TagOperation;

public class NurHardwareManagerCLI {

	public static void main(String[] args) {
		RFConsumer rfCons = new RFConsumer() {
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

		CommunicationHandler cHdl = new CommunicationHandler();

		boolean running = true;
		try (Scanner sc = new Scanner(System.in)) {
			System.out.println("Type 'q' to quit. Type '?' for help.");

			while (running) {
				System.out.print("rf-nur>");
				String cmd = sc.nextLine();
				cmd = cmd.replaceAll("\\s+", "");

				if (0 == cmd.length())
					continue;

				if (cmd.equals("q")) {
					System.out.println("Closing handler...");
					try {
						cHdl.closeConnection();
						System.out.println("Handler closed...");
					} catch (ConnectionException e) {
						e.printStackTrace();
					}
					running = false;
				}

				else if (cmd.equals("open")) {
					System.out.println("Opening connection...");
					try {
						cHdl.openConnection(rfCons, 500);
						System.out.println("OK");
					} catch (ConnectionException | ImplementationException e) {
						e.printStackTrace();
					}
				}

				else if (cmd.equals("close")) {
					System.out.println("Closing connection...");
					try {
						cHdl.closeConnection();
						System.out.println("OK");
					} catch (ConnectionException e) {
						e.printStackTrace();
					}
				}

				else if (cmd.equals("reg-eu")) {
					System.out.println("Setting region EU");
					try {
						cHdl.setRegion("EU");
						System.out.println("OK");
					} catch (ConnectionException | ParameterException | ImplementationException e) {
						e.printStackTrace();
					}
				}

				else if (cmd.equals("reg-fcc")) {
					System.out.println("Setting region FCC");
					try {
						cHdl.setRegion("FCC");
						System.out.println("OK");
					} catch (ConnectionException | ParameterException | ImplementationException e) {
						e.printStackTrace();
					}
				}

				else if (cmd.equals("reg-unspec")) {
					System.out.println("Setting region Unspecified");
					try {
						cHdl.setRegion("Unspecified");
						System.out.println("OK");
					} catch (ConnectionException | ParameterException | ImplementationException e) {
						e.printStackTrace();
					}
				}

				else if (cmd.endsWith("-props")) {
					try {
						short antenna = 0;

						if (cmd.startsWith("ant-1-"))
							antenna = 1;
						else if (cmd.startsWith("ant-2-"))
							antenna = 2;

						if (antenna == 0)
							System.out.println("Bad antenna number.");
						else {
							List<Configuration> configs = cHdl.getConfiguration(ConfigurationType.ANTENNA_PROPERTIES, antenna, (short) 0, (short) 0);
							AntennaProperties aProps = (AntennaProperties) configs.get(0);
							System.out.println("Antenna: " + aProps.getId());
							System.out.println("Connected: " + aProps.isConnected());
						}

						System.out.println("OK");

					} catch (ConnectionException | ImplementationException e) {
						e.printStackTrace();
					}
				}

				else if (cmd.startsWith("ant-")) {
					short antenna = 0;
					ConnectType cType = null;

					if (cmd.startsWith("ant-1-"))
						antenna = 1;
					else if (cmd.startsWith("ant-2-"))
						antenna = 2;

					if (cmd.endsWith("-off"))
						cType = ConnectType.FALSE;
					else if (cmd.endsWith("-on"))
						cType = ConnectType.TRUE;
					else if (cmd.endsWith("-auto"))
						cType = ConnectType.AUTO;

					if (antenna == 0)
						System.out.println("Bad antenna number.");
					else if (cType == null)
						System.out.println("Bad connect type.");
					else {
						System.out.println("Setting connect type of antenna " + antenna + " to state: " + cType);

						try {
							List<Configuration> configs = cHdl.getConfiguration(ConfigurationType.ANTENNA_CONFIGURATION, antenna, (short) 0, (short) 0);

							AntennaConfiguration aCfg = (AntennaConfiguration) configs.get(0);
							aCfg.setConnect(cType);
							cHdl.setConfiguration(configs);
							System.out.println("OK");

						} catch (ConnectionException | ImplementationException | ParameterException e) {
							e.printStackTrace();
						}
					}
				}

				else if (cmd.endsWith("scan")) {
					try {
						List<TagData> tags = cHdl.execute(Arrays.asList(new Short[] { 1, 2 }), new ArrayList<Filter>(), new ArrayList<TagOperation>());

						for (TagData tag : tags)
							System.out.println(RFUtils.bytesToHex(tag.getEpc()));

						System.out.println("OK");
					} catch (ConnectionException | ImplementationException | CommunicationException | ParameterException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("Unknown command: " + cmd);
				}

			}
		}
		System.exit(0);
	}
}
