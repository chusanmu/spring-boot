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

package org.springframework.boot.context;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * TODO: 主要用来注册一个bean, ContextId 这个bean
 * {@link ApplicationContextInitializer} that sets the Spring
 * {@link ApplicationContext#getId() ApplicationContext ID}. The
 * {@code spring.application.name} property is used to create the ID. If the property is
 * not set {@code application} is used.
 *
 * @author Dave Syer
 * @author Andy Wilkinson
 * @since 1.0.0
 */
public class ContextIdApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	private int order = Ordered.LOWEST_PRECEDENCE - 10;

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		// TODO: 尝试获得ContextId
		ContextId contextId = getContextId(applicationContext);
		// TODO: 把contextId从容器中拿到
		applicationContext.setId(contextId.getId());
		// TODO: 然后向容器注册这个bean
		applicationContext.getBeanFactory().registerSingleton(ContextId.class.getName(), contextId);
	}

	private ContextId getContextId(ConfigurableApplicationContext applicationContext) {
		// TODO: 先把它的父容器拿到
		ApplicationContext parent = applicationContext.getParent();
		// TODO: 如果父容器不等于空，并且包括这个类型的bean
		if (parent != null && parent.containsBean(ContextId.class.getName())) {
			// TODO: 那就把它拿出来，创建一个ChildId
			return parent.getBean(ContextId.class).createChildId();
		}
		// TODO: 否则直接创建，取应用名，如果没有应用名，则取默认值application
		return new ContextId(getApplicationId(applicationContext.getEnvironment()));
	}

	private String getApplicationId(ConfigurableEnvironment environment) {
		String name = environment.getProperty("spring.application.name");
		return StringUtils.hasText(name) ? name : "application";
	}

	/**
	 * The ID of a context.
	 */
	static class ContextId {

		private final AtomicLong children = new AtomicLong(0);

		private final String id;

		ContextId(String id) {
			this.id = id;
		}

		ContextId createChildId() {
			return new ContextId(this.id + "-" + this.children.incrementAndGet());
		}

		String getId() {
			return this.id;
		}

	}

}
