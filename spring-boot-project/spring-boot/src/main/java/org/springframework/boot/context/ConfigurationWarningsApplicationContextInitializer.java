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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * TODO: 此类的作用就是添加一个 beanFactoryPostProcessor, 去校验 @ComponentScan扫描到的包
 * {@link ApplicationContextInitializer} to report warnings for common misconfiguration
 * mistakes.
 *
 * @author Phillip Webb
 * @since 1.2.0
 */
public class ConfigurationWarningsApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final Log logger = LogFactory.getLog(ConfigurationWarningsApplicationContextInitializer.class);

	/**
	 * TODO: 这里也只是加了个 beanFactoryPostProcessor
	 * @param context
	 */
	@Override
	public void initialize(ConfigurableApplicationContext context) {
		// TODO: 添加一个BeanFactoryPostProcessor
		context.addBeanFactoryPostProcessor(new ConfigurationWarningsPostProcessor(getChecks()));
	}

	/**
	 * TODO: 在initialize的时候加入了 一个Check
	 * Returns the checks that should be applied.
	 * @return the checks to apply
	 */
	protected Check[] getChecks() {
		return new Check[] { new ComponentScanPackageCheck() };
	}

	/**
	 * {@link BeanDefinitionRegistryPostProcessor} to report warnings.
	 */
	protected static final class ConfigurationWarningsPostProcessor
			implements PriorityOrdered, BeanDefinitionRegistryPostProcessor {

		private Check[] checks;

		public ConfigurationWarningsPostProcessor(Check[] checks) {
			this.checks = checks;
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE - 1;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		}

		/**
		 * TODO: 真正执行的时候，会走到这儿
		 * @param registry
		 * @throws BeansException
		 */
		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
			// TODO: 遍历所有的checks, 然后得到message, 如果有message，那就打印出来一个警告.
			for (Check check : this.checks) {
				String message = check.getWarning(registry);
				if (StringUtils.hasLength(message)) {
					warn(message);
				}
			}

		}

		private void warn(String message) {
			if (logger.isWarnEnabled()) {
				logger.warn(String.format("%n%n** WARNING ** : %s%n%n", message));
			}
		}

	}

	/**
	 * A single check that can be applied.
	 */
	@FunctionalInterface
	protected interface Check {

		/**
		 * Returns a warning if the check fails or {@code null} if there are no problems.
		 * @param registry the {@link BeanDefinitionRegistry}
		 * @return a warning message or {@code null}
		 */
		String getWarning(BeanDefinitionRegistry registry);

	}

	/**
	 * TODO: 组件扫描 check
	 * {@link Check} for {@code @ComponentScan} on problematic package.
	 */
	protected static class ComponentScanPackageCheck implements Check {

		private static final Set<String> PROBLEM_PACKAGES;

		/**
		 * 添加了些不允许扫描的包
		 */
		static {
			Set<String> packages = new HashSet<>();
			packages.add("org.springframework");
			packages.add("org");
			PROBLEM_PACKAGES = Collections.unmodifiableSet(packages);
		}

		@Override
		public String getWarning(BeanDefinitionRegistry registry) {
			// TODO: 获取所有的@ComponentScan，所扫描的路径
			Set<String> scannedPackages = getComponentScanningPackages(registry);
			// TODO: 得到有问题的路径
			List<String> problematicPackages = getProblematicPackages(scannedPackages);
			// TODO: 如果没有有问题的，那就啥都不做
			if (problematicPackages.isEmpty()) {
				return null;
			}
			return "Your ApplicationContext is unlikely to start due to a @ComponentScan of "
					+ StringUtils.collectionToDelimitedString(problematicPackages, ", ") + ".";
		}

		protected Set<String> getComponentScanningPackages(BeanDefinitionRegistry registry) {
			Set<String> packages = new LinkedHashSet<>();
			// TODO: 把容器中所有的beanDefinitionNames拿到
			String[] names = registry.getBeanDefinitionNames();
			// TODO: 然后进行遍历
			for (String name : names) {
				// TODO: 把指定的一个beanDefinition拿到
				BeanDefinition definition = registry.getBeanDefinition(name);
				if (definition instanceof AnnotatedBeanDefinition) {
					AnnotatedBeanDefinition annotatedDefinition = (AnnotatedBeanDefinition) definition;
					addComponentScanningPackages(packages, annotatedDefinition.getMetadata());
				}
			}
			return packages;
		}

		private void addComponentScanningPackages(Set<String> packages, AnnotationMetadata metadata) {
			// TODO: 尝试获取ComponentScan注解属性值
			AnnotationAttributes attributes = AnnotationAttributes
					.fromMap(metadata.getAnnotationAttributes(ComponentScan.class.getName(), true));
			// TODO: 如果不为空，表示获取到了
			if (attributes != null) {
				// TODO: 把ComponentScan中对应的value值 加到 packages中
				addPackages(packages, attributes.getStringArray("value"));
				addPackages(packages, attributes.getStringArray("basePackages"));
				addClasses(packages, attributes.getStringArray("basePackageClasses"));
				// TODO: 如果ComponentScan中没有指定package, 那么这里添加的是那个类它所在的那个包
				if (packages.isEmpty()) {
					packages.add(ClassUtils.getPackageName(metadata.getClassName()));
				}
			}
		}

		private void addPackages(Set<String> packages, String[] values) {
			if (values != null) {
				Collections.addAll(packages, values);
			}
		}

		private void addClasses(Set<String> packages, String[] values) {
			if (values != null) {
				for (String value : values) {
					packages.add(ClassUtils.getPackageName(value));
				}
			}
		}

		private List<String> getProblematicPackages(Set<String> scannedPackages) {
			List<String> problematicPackages = new ArrayList<>();
			// TODO: 看看扫的这个包是不是有问题的，进行遍历
			for (String scannedPackage : scannedPackages) {
				// TODO: 如果是有问题的，那就加到 problematicPackages中
				if (isProblematicPackage(scannedPackage)) {
					problematicPackages.add(getDisplayName(scannedPackage));
				}
			}
			return problematicPackages;
		}

		private boolean isProblematicPackage(String scannedPackage) {
			// TODO: 扫描的包为null或者为空，那肯定是有问题的，
			if (scannedPackage == null || scannedPackage.isEmpty()) {
				return true;
			}
			// TODO: 如果扫的包是 org.springframework， org,这俩，那么也被认为是有问题的
			return PROBLEM_PACKAGES.contains(scannedPackage);
		}

		private String getDisplayName(String scannedPackage) {
			if (scannedPackage == null || scannedPackage.isEmpty()) {
				return "the default package";
			}
			return "'" + scannedPackage + "'";
		}

	}

}
