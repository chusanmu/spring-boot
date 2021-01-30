/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.web.embedded.tomcat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.naming.NamingException;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.naming.ContextBindings;

import org.springframework.boot.web.server.GracefulShutdownCallback;
import org.springframework.boot.web.server.GracefulShutdownResult;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.util.Assert;

/**
 * TODO: tomcat web server 实现了 webServer的接口，里面封装了 tomcat
 * {@link WebServer} that can be used to control a Tomcat web server. Usually this class
 * should be created using the {@link TomcatReactiveWebServerFactory} of
 * {@link TomcatServletWebServerFactory}, but not directly.
 *
 * @author Brian Clozel
 * @author Kristine Jetzke
 * @since 2.0.0
 */
public class TomcatWebServer implements WebServer {

	private static final Log logger = LogFactory.getLog(TomcatWebServer.class);

	private static final AtomicInteger containerCounter = new AtomicInteger(-1);

	private final Object monitor = new Object();

	/**
	 * 临时保存每个service对应的connector
	 */
	private final Map<Service, Connector[]> serviceConnectors = new HashMap<>();

	/**
	 * TODO: 封装了一个tomcat
	 */
	private final Tomcat tomcat;

	private final boolean autoStart;

	private final GracefulShutdown gracefulShutdown;

	private volatile boolean started;

	/**
	 * Create a new {@link TomcatWebServer} instance.
	 * @param tomcat the underlying Tomcat server
	 */
	public TomcatWebServer(Tomcat tomcat) {
		this(tomcat, true);
	}

	/**
	 * Create a new {@link TomcatWebServer} instance.
	 * @param tomcat the underlying Tomcat server
	 * @param autoStart if the server should be started
	 */
	public TomcatWebServer(Tomcat tomcat, boolean autoStart) {
		this(tomcat, autoStart, Shutdown.IMMEDIATE);
	}

	/**
	 * Create a new {@link TomcatWebServer} instance.
	 * @param tomcat the underlying Tomcat server
	 * @param autoStart if the server should be started
	 * @param shutdown type of shutdown supported by the server
	 * @since 2.3.0
	 */
	public TomcatWebServer(Tomcat tomcat, boolean autoStart, Shutdown shutdown) {
		Assert.notNull(tomcat, "Tomcat Server must not be null");
		this.tomcat = tomcat;
		this.autoStart = autoStart;
		this.gracefulShutdown = (shutdown == Shutdown.GRACEFUL) ? new GracefulShutdown(tomcat) : null;
		// TODO: 进行初始化，会进行启动tomcat
		initialize();
	}

	private void initialize() throws WebServerException {
		// TODO: 这个日志 我们可能会经常看到
		logger.info("Tomcat initialized with port(s): " + getPortsDescription(false));
		synchronized (this.monitor) {
			try {
				addInstanceIdToEngineName();

				Context context = findContext();
				// TODO: 添加一个生命周期监听器
				context.addLifecycleListener((event) -> {
					// TODO: 事件源是当前context触发的，并且事件类型是start
					if (context.equals(event.getSource()) && Lifecycle.START_EVENT.equals(event.getType())) {
						// Remove service connectors so that protocol binding doesn't
						// happen when the service is started.
						// TODO: 删除服务连接器，以便在启动服务时不会发生协议绑定。移除了service的connector，
						//  所以下面tomcat在start的时候，不会绑定端口开启的.
						removeServiceConnectors();
					}
				});

				// Start the server to trigger initialization listeners
				// TODO: 开启tomcat服务，会触发初始化监听器, 然后利用applicationContext容器，
				//  拿出servlet, filter等添加到tomcat容器中，不会启动connector，因为上面移除了
				this.tomcat.start();

				// We can re-throw failure exception directly in the main thread
				// TODO: 将异常重新抛出，抛出initializing过程的异常，抛出tomcat启动过程中的异常
				rethrowDeferredStartupExceptions();

				try {
					// TODO: 绑定classLoader
					ContextBindings.bindClassLoader(context, context.getNamingToken(), getClass().getClassLoader());
				}
				catch (NamingException ex) {
					// Naming is not enabled. Continue
				}

				// Unlike Jetty, all Tomcat threads are daemon threads. We create a
				// blocking non-daemon to stop immediate shutdown
				// TODO: 启动一个非守护线程 来等待停止tomcat
				startDaemonAwaitThread();
			}
			catch (Exception ex) {
				stopSilently();
				destroySilently();
				throw new WebServerException("Unable to start embedded Tomcat", ex);
			}
		}
	}

	private Context findContext() {
		for (Container child : this.tomcat.getHost().findChildren()) {
			if (child instanceof Context) {
				return (Context) child;
			}
		}
		throw new IllegalStateException("The host does not contain a Context");
	}

	private void addInstanceIdToEngineName() {
		int instanceId = containerCounter.incrementAndGet();
		if (instanceId > 0) {
			Engine engine = this.tomcat.getEngine();
			engine.setName(engine.getName() + "-" + instanceId);
		}
	}

	private void removeServiceConnectors() {
		for (Service service : this.tomcat.getServer().findServices()) {
			Connector[] connectors = service.findConnectors().clone();
			this.serviceConnectors.put(service, connectors);
			for (Connector connector : connectors) {
				service.removeConnector(connector);
			}
		}
	}

	private void rethrowDeferredStartupExceptions() throws Exception {
		Container[] children = this.tomcat.getHost().findChildren();
		for (Container container : children) {
			if (container instanceof TomcatEmbeddedContext) {
				TomcatStarter tomcatStarter = ((TomcatEmbeddedContext) container).getStarter();
				if (tomcatStarter != null) {
					Exception exception = tomcatStarter.getStartUpException();
					if (exception != null) {
						throw exception;
					}
				}
			}
			if (!LifecycleState.STARTED.equals(container.getState())) {
				throw new IllegalStateException(container + " failed to start");
			}
		}
	}

	private void startDaemonAwaitThread() {
		Thread awaitThread = new Thread("container-" + (containerCounter.get())) {

			@Override
			public void run() {
				TomcatWebServer.this.tomcat.getServer().await();
			}

		};
		awaitThread.setContextClassLoader(getClass().getClassLoader());
		awaitThread.setDaemon(false);
		awaitThread.start();
	}

	/**
	 * TODO: 开启tomcat
	 *
	 * @throws WebServerException
	 */
	@Override
	public void start() throws WebServerException {
		synchronized (this.monitor) {
			// TODO: 如果已经开启完了 就直接返回了
			if (this.started) {
				return;
			}
			try {
				// TODO: 将之前已经移除了的connector再次加到service中
				addPreviouslyRemovedConnectors();
				// TODO: 获取service对应的connector
				Connector connector = this.tomcat.getConnector();
				if (connector != null && this.autoStart) {
					// TODO: 回去调用org.apache.catalina.core.StandardWrapper.load()方法，开始初始化servlet
					performDeferredLoadOnStartup();
				}
				// TODO: 检查connector是否已经启动 成功，没有启动成功的话，直接抛出异常
				checkThatConnectorsHaveStarted();
				this.started = true;
				logger.info("Tomcat started on port(s): " + getPortsDescription(true) + " with context path '"
						+ getContextPath() + "'");
			}
			catch (ConnectorStartFailedException ex) {
				stopSilently();
				throw ex;
			}
			catch (Exception ex) {
				PortInUseException.throwIfPortBindingException(ex, () -> this.tomcat.getConnector().getPort());
				throw new WebServerException("Unable to start embedded Tomcat server", ex);
			}
			finally {
				Context context = findContext();
				ContextBindings.unbindClassLoader(context, context.getNamingToken(), getClass().getClassLoader());
			}
		}
	}

	private void checkThatConnectorsHaveStarted() {
		checkConnectorHasStarted(this.tomcat.getConnector());
		for (Connector connector : this.tomcat.getService().findConnectors()) {
			checkConnectorHasStarted(connector);
		}
	}

	private void checkConnectorHasStarted(Connector connector) {
		if (LifecycleState.FAILED.equals(connector.getState())) {
			throw new ConnectorStartFailedException(connector.getPort());
		}
	}

	private void stopSilently() {
		try {
			stopTomcat();
		}
		catch (LifecycleException ex) {
			// Ignore
		}
	}

	private void destroySilently() {
		try {
			this.tomcat.destroy();
		}
		catch (LifecycleException ex) {
			// Ignore
		}
	}

	private void stopTomcat() throws LifecycleException {
		if (Thread.currentThread().getContextClassLoader() instanceof TomcatEmbeddedWebappClassLoader) {
			Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		}
		this.tomcat.stop();
	}


	/**
	 * TODO: 其目的是 将 serviceConnectors 中的connector加到service中
	 */
	private void addPreviouslyRemovedConnectors() {
		// TODO: 获取tomcat server所有的services，然后挨个遍历
		Service[] services = this.tomcat.getServer().findServices();
		for (Service service : services) {
			// TODO: 根据service拿到它的Connector
			Connector[] connectors = this.serviceConnectors.get(service);
			// TODO: 如果connectors不为空，然后进行遍历
			if (connectors != null) {
				for (Connector connector : connectors) {
					// TODO: 将connector加到service中, 这时候会自动启动connector
					service.addConnector(connector);
					if (!this.autoStart) {
						stopProtocolHandler(connector);
					}
				}
				// TODO: 最后进行移除
				this.serviceConnectors.remove(service);
			}
		}
	}

	private void stopProtocolHandler(Connector connector) {
		try {
			connector.getProtocolHandler().stop();
		}
		catch (Exception ex) {
			logger.error("Cannot pause connector: ", ex);
		}
	}

	private void performDeferredLoadOnStartup() {
		try {
			for (Container child : this.tomcat.getHost().findChildren()) {
				if (child instanceof TomcatEmbeddedContext) {
					((TomcatEmbeddedContext) child).deferredLoadOnStartup();
				}
			}
		}
		catch (Exception ex) {
			if (ex instanceof WebServerException) {
				throw (WebServerException) ex;
			}
			throw new WebServerException("Unable to start embedded Tomcat connectors", ex);
		}
	}

	Map<Service, Connector[]> getServiceConnectors() {
		return this.serviceConnectors;
	}

	@Override
	public void stop() throws WebServerException {
		synchronized (this.monitor) {
			boolean wasStarted = this.started;
			try {
				this.started = false;
				try {
					if (this.gracefulShutdown != null) {
						this.gracefulShutdown.abort();
					}
					stopTomcat();
					this.tomcat.destroy();
				}
				catch (LifecycleException ex) {
					// swallow and continue
				}
			}
			catch (Exception ex) {
				throw new WebServerException("Unable to stop embedded Tomcat", ex);
			}
			finally {
				if (wasStarted) {
					containerCounter.decrementAndGet();
				}
			}
		}
	}

	private String getPortsDescription(boolean localPort) {
		StringBuilder ports = new StringBuilder();
		for (Connector connector : this.tomcat.getService().findConnectors()) {
			if (ports.length() != 0) {
				ports.append(' ');
			}
			int port = localPort ? connector.getLocalPort() : connector.getPort();
			ports.append(port).append(" (").append(connector.getScheme()).append(')');
		}
		return ports.toString();
	}

	@Override
	public int getPort() {
		Connector connector = this.tomcat.getConnector();
		if (connector != null) {
			return connector.getLocalPort();
		}
		return 0;
	}

	private String getContextPath() {
		return Arrays.stream(this.tomcat.getHost().findChildren()).filter(TomcatEmbeddedContext.class::isInstance)
				.map(TomcatEmbeddedContext.class::cast).map(TomcatEmbeddedContext::getPath)
				.collect(Collectors.joining(" "));
	}

	/**
	 * Returns access to the underlying Tomcat server.
	 * @return the Tomcat server
	 */
	public Tomcat getTomcat() {
		return this.tomcat;
	}

	@Override
	public void shutDownGracefully(GracefulShutdownCallback callback) {
		if (this.gracefulShutdown == null) {
			callback.shutdownComplete(GracefulShutdownResult.IMMEDIATE);
			return;
		}
		this.gracefulShutdown.shutDownGracefully(callback);
	}

}
