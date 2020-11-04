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

package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link ApplicationContextInitializer} that delegates to other initializers that are
 * specified under a {@literal context.initializer.classes} environment property.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @since 1.0.0
 */
public class DelegatingApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	// NOTE: Similar to org.springframework.web.context.ContextLoader


	/**
	 * TODO: 去环境中加载这个属性
	 */
	private static final String PROPERTY_NAME = "context.initializer.classes";

	private int order = 0;

	/**
	 * TODO: initialize初始化的时候 会调用此方法
	 * @param context
	 */
	@Override
	public void initialize(ConfigurableApplicationContext context) {
		// TODO: 把当前环境 environment拿到
		ConfigurableEnvironment environment = context.getEnvironment();
		// TODO: 拿到需要初始化的class类的集合
		List<Class<?>> initializerClasses = getInitializerClasses(environment);
		// TODO: 如果需要初始化的class不为空
		if (!initializerClasses.isEmpty()) {
			// TODO: 去应用它的 ApplicationContextInitializer 中的 initialize 方法
			applyInitializerClasses(context, initializerClasses);
		}
	}

	private List<Class<?>> getInitializerClasses(ConfigurableEnvironment env) {
		// TODO: 去获取需要初始化的className
		String classNames = env.getProperty(PROPERTY_NAME);
		List<Class<?>> classes = new ArrayList<>();
		// TODO: 如果className不为空，就去用逗号分隔 然后进行遍历
		if (StringUtils.hasLength(classNames)) {
			for (String className : StringUtils.tokenizeToStringArray(classNames, ",")) {
				classes.add(getInitializerClass(className));
			}
		}
		return classes;
	}

	private Class<?> getInitializerClass(String className) throws LinkageError {
		try {
			// TODO: 加载类，然后判断所属类型是否正确，是否为 ApplicationContextInitializer
			Class<?> initializerClass = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
			Assert.isAssignable(ApplicationContextInitializer.class, initializerClass);
			// TODO: 如果一切正确，最后返回initializerClass
			return initializerClass;
		}
		catch (ClassNotFoundException ex) {
			throw new ApplicationContextException("Failed to load context initializer class [" + className + "]", ex);
		}
	}

	private void applyInitializerClasses(ConfigurableApplicationContext context, List<Class<?>> initializerClasses) {
		Class<?> contextClass = context.getClass();
		List<ApplicationContextInitializer<?>> initializers = new ArrayList<>();
		// TODO: 去遍历所有的class,然后进行实例化
		for (Class<?> initializerClass : initializerClasses) {
			initializers.add(instantiateInitializer(contextClass, initializerClass));
		}
		// TODO: 把所有的class的实例拿到
		applyInitializers(context, initializers);
	}

	private ApplicationContextInitializer<?> instantiateInitializer(Class<?> contextClass, Class<?> initializerClass) {
		Class<?> requireContextClass = GenericTypeResolver.resolveTypeArgument(initializerClass,
				ApplicationContextInitializer.class);
		// TODO: 判断这个Initializer的泛型是否正确 必须是 ConfigurableApplicationContext 这种类型的
		Assert.isAssignable(requireContextClass, contextClass,
				String.format(
						"Could not add context initializer [%s] as its generic parameter [%s] is not assignable "
								+ "from the type of application context used by this context loader [%s]: ",
						initializerClass.getName(), requireContextClass.getName(), contextClass.getName()));
		// TODO: 然后通过校验，去使用BeanUtils去实例化它
		return (ApplicationContextInitializer<?>) BeanUtils.instantiateClass(initializerClass);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void applyInitializers(ConfigurableApplicationContext context,
			List<ApplicationContextInitializer<?>> initializers) {
		// TODO: 这里还会对所有的initializers进行一个排序
		initializers.sort(new AnnotationAwareOrderComparator());
		// TODO: 然后遍历，执行它们的 initialize 方法
		for (ApplicationContextInitializer initializer : initializers) {
			initializer.initialize(context);
		}
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

}
