package havis.device.rf.nur.osgi;

import havis.device.rf.RFDevice;
import havis.device.rf.common.CommunicationHandler;
import havis.device.rf.exception.ImplementationException;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;

/**
 * <p>
 * OSGi bundle Activator class that registers an RFDeviceFactory as service. The
 * service is registered using the naming from bundle properties that have to be
 * provided by the OSGi container. The name(s) the service is registered with,
 * consists of a host and a port and can be retrieved using the pattern
 * <b>(&(host=&lt;HOST&gt;)(port=&lt;PORT&gt;))</b>. The bundle properties to
 * declare the names are <b>havis.embedded.rfc.service.host.<i>i</i></b> and
 * <b>havis.embedded.rfc.service.port.<i>i</i></b> whereas <b><i>i</i></b> is a
 * number ranging from 0 to N, resulting in the service being registered N+1
 * times under the corresponding names
 * </p>
 * 
 * <p>
 * A registered factory's getInstance method is synchronized as well as the
 * hardware access.
 * </p>
 * 
 */

public class Activator implements BundleActivator {

	private BundleContext bundleContext;
	private ServiceRegistration<?> rfcServiceRegistration;

	/**
	 * Is called by the OSGi container once the bundle is started. This listener
	 * method is used to register the {@link ServiceFactory} service as
	 * described above.
	 * 
	 * @param bundleContext
	 *            the BundleContext instance.
	 */
	@Override
	public void start(BundleContext bundleContext) throws Exception {
		this.bundleContext = bundleContext;
		registerService();
	}

	/**
	 * Is called by the OSGi container once the bundle is stopped. This listener
	 * method is called to unregister an {@link ServiceFactory} service
	 * instances.
	 * 
	 * @param bundleContext
	 *            the BundleContext instance.
	 */
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		unregisterService();
	}

	protected void registerService() throws ImplementationException {
		this.rfcServiceRegistration = bundleContext.registerService(RFDevice.class.getName(), new ServiceFactory<RFDevice>() {
			@Override
			public RFDevice getService(Bundle bundle, ServiceRegistration<RFDevice> registration) {
				ClassLoader current = Thread.currentThread().getContextClassLoader();
				try {
					Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
					return new CommunicationHandler();
				} finally {
					Thread.currentThread().setContextClassLoader(current);
				}
			}

			@Override
			public void ungetService(Bundle bundle, ServiceRegistration<RFDevice> registration, RFDevice service) {
				/* RFU */
			}
		}, null);
	}

	protected void unregisterService() {
		if (this.rfcServiceRegistration != null)
			this.rfcServiceRegistration.unregister();

		CommunicationHandler.dispose();
	}
}
