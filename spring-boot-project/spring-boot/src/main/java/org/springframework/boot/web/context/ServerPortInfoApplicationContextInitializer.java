/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.boot.web.context;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

/**
 * TODO: 把当前webServer对应的端口号加到环境中，方便你在应用中 直接进行注入 @Value("${local.server.port}") 这样直接获取端口号
 *
 * {@link ApplicationContextInitializer} that sets {@link Environment} properties for the
 * ports that {@link WebServer} servers are actually listening on. The property
 * {@literal "local.server.port"} can be injected directly into tests using
 * {@link Value @Value} or obtained via the {@link Environment}.
 * <p>
 * If the {@link WebServerInitializedEvent} has a
 * {@link WebServerApplicationContext#getServerNamespace() server namespace} , it will be
 * used to construct the property name. For example, the "management" actuator context
 * will have the property name {@literal "local.management.port"}.
 * <p>
 * Properties are automatically propagated up to any parent context.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @since 2.0.0
 */
public class ServerPortInfoApplicationContextInitializer implements
		ApplicationContextInitializer<ConfigurableApplicationContext>, ApplicationListener<WebServerInitializedEvent> {

	/**
	 * TODO: 直接注册了一个监听器，就是它自己，监听webServerInitializedEvent
	 * @param applicationContext
	 */
	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		applicationContext.addApplicationListener(this);
	}

	@Override
	public void onApplicationEvent(WebServerInitializedEvent event) {
		// TODO: 构建属性名
		String propertyName = "local." + getName(event.getApplicationContext()) + ".port";
		// TODO: 把当前端口号 加到环境里面去
		setPortProperty(event.getApplicationContext(), propertyName, event.getWebServer().getPort());
	}

	private String getName(WebServerApplicationContext context) {
		String name = context.getServerNamespace();
		return StringUtils.hasText(name) ? name : "server";
	}

	private void setPortProperty(ApplicationContext context, String propertyName, int port) {
		// TODO: 往运行环境中添加变量
		if (context instanceof ConfigurableApplicationContext) {
			setPortProperty(((ConfigurableApplicationContext) context).getEnvironment(), propertyName, port);
		}
		// TODO: 如果存在父容器，也会在父容器里面设上这个值
		if (context.getParent() != null) {
			setPortProperty(context.getParent(), propertyName, port);
		}
	}

	@SuppressWarnings("unchecked")
	private void setPortProperty(ConfigurableEnvironment environment, String propertyName, int port) {
		MutablePropertySources sources = environment.getPropertySources();
		PropertySource<?> source = sources.get("server.ports");
		if (source == null) {
			source = new MapPropertySource("server.ports", new HashMap<>());
			sources.addFirst(source);
		}
		((Map<String, Object>) source.getSource()).put(propertyName, port);
	}

}
