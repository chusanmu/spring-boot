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

package org.springframework.boot.autoconfigure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * TODO: 开启自动装配后 自动导入的类，此类非常重要，属于Spring boot的核心类了
 *
 * {@link DeferredImportSelector} to handle {@link EnableAutoConfiguration
 * auto-configuration}. This class can also be subclassed if a custom variant of
 * {@link EnableAutoConfiguration @EnableAutoConfiguration} is needed.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Madhura Bhave
 * @since 1.3.0
 * @see EnableAutoConfiguration
 */
public class AutoConfigurationImportSelector implements DeferredImportSelector, BeanClassLoaderAware,
		ResourceLoaderAware, BeanFactoryAware, EnvironmentAware, Ordered {

	private static final AutoConfigurationEntry EMPTY_ENTRY = new AutoConfigurationEntry();

	private static final String[] NO_IMPORTS = {};

	private static final Log logger = LogFactory.getLog(AutoConfigurationImportSelector.class);

	/**
	 * TODO: 可以配置一个环境变量，用来去除你不想自动加载的类
	 */
	private static final String PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE = "spring.autoconfigure.exclude";

	private ConfigurableListableBeanFactory beanFactory;

	private Environment environment;

	private ClassLoader beanClassLoader;

	private ResourceLoader resourceLoader;

	private ConfigurationClassFilter configurationClassFilter;

	@Override
	public String[] selectImports(AnnotationMetadata annotationMetadata) {
		// TODO: 如果没开启自动装配，那就根本不到了，直接 返回一个空数组
		if (!isEnabled(annotationMetadata)) {
			return NO_IMPORTS;
		}
		// TODO: 得到了AutoConfigurationEntry， 这里封装了过滤后的configurations以及需要排除的exclusions configuration
		AutoConfigurationEntry autoConfigurationEntry = getAutoConfigurationEntry(annotationMetadata);
		// TODO: 这里才是返回了需要进行自动装配的所有的spring.factories中合法的configuration
		return StringUtils.toStringArray(autoConfigurationEntry.getConfigurations());
	}


	/**
	 *  TODO: getExclusionFilter 是 5.2.4中新加的接口,用来判断当前import的类是否合法，在 org.springframework.context.annotation.ConfigurationClassParser#processImports() 的时候进行调用
	 *
	 * @return
	 */
	@Override
	public Predicate<String> getExclusionFilter() {
		return this::shouldExclude;
	}


	private boolean shouldExclude(String configurationClassName) {
		return getConfigurationClassFilter().filter(Collections.singletonList(configurationClassName)).isEmpty();
	}

	/**
	 * Return the {@link AutoConfigurationEntry} based on the {@link AnnotationMetadata}
	 * of the importing {@link Configuration @Configuration} class.
	 * @param annotationMetadata the annotation metadata of the configuration class
	 * @return the auto-configurations that should be imported
	 */
	protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata annotationMetadata) {
		// TODO: 再去判断下，有没有开启 自动装配
		if (!isEnabled(annotationMetadata)) {
			return EMPTY_ENTRY;
		}
		// TODO: 拿到当前注解所有的属性值
		AnnotationAttributes attributes = getAttributes(annotationMetadata);
		// TODO: 这时候开始加载了，去加载所有的 EnableAutoConfiguration 对应的配置文件的所有的名字拿到
		List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);
		// TODO: 进行去重
		configurations = removeDuplicates(configurations);
		// TODO: 得到排除的类名，不需要自动装配的类名
		Set<String> exclusions = getExclusions(annotationMetadata, attributes);
		// TODO: 这地方如果配错了还会报异常
		checkExcludedClasses(configurations, exclusions);
		// TODO: 把所有需要排除的类，进行remove
		configurations.removeAll(exclusions);
		// TODO: 过滤出符合条件的configuration，其实这里的filter只能做一个初步的判断，并不能做详细的判断
		// TODO: 它只在selectImport导入的时候，判断这个自动配置类是否需要导入，而之后解析配置，配置@Bean的时候，还会专门为filter
		configurations = getConfigurationClassFilter().filter(configurations);
		// TODO: 传播一个事件
		fireAutoConfigurationImportEvents(configurations, exclusions);
		// TODO: 把configurations和exclusions包成AutoConfigurationEntry 进行返回
		return new AutoConfigurationEntry(configurations, exclusions);
	}

	@Override
	public Class<? extends Group> getImportGroup() {
		return AutoConfigurationGroup.class;
	}

	/**
	 * TODO: 判断是否开启了自动装配，默认是开启了的
	 * @param metadata
	 * @return
	 */
	protected boolean isEnabled(AnnotationMetadata metadata) {
		if (getClass() == AutoConfigurationImportSelector.class) {
			return getEnvironment().getProperty(EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY, Boolean.class, true);
		}
		return true;
	}

	/**
	 * Return the appropriate {@link AnnotationAttributes} from the
	 * {@link AnnotationMetadata}. By default this method will return attributes for
	 * {@link #getAnnotationClass()}.
	 * @param metadata the annotation metadata
	 * @return annotation attributes
	 */
	protected AnnotationAttributes getAttributes(AnnotationMetadata metadata) {
		String name = getAnnotationClass().getName();
		AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(name, true));
		Assert.notNull(attributes, () -> "No auto-configuration attributes found. Is " + metadata.getClassName()
				+ " annotated with " + ClassUtils.getShortName(name) + "?");
		return attributes;
	}

	/**
	 * Return the source annotation class used by the selector.
	 * @return the annotation class
	 */
	protected Class<?> getAnnotationClass() {
		return EnableAutoConfiguration.class;
	}

	/**
	 * Return the auto-configuration class names that should be considered. By default
	 * this method will load candidates using {@link SpringFactoriesLoader} with
	 * {@link #getSpringFactoriesLoaderFactoryClass()}.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return a list of candidate configurations
	 */
	protected List<String> getCandidateConfigurations(AnnotationMetadata metadata, AnnotationAttributes attributes) {
		List<String> configurations = SpringFactoriesLoader.loadFactoryNames(getSpringFactoriesLoaderFactoryClass(),
				getBeanClassLoader());
		Assert.notEmpty(configurations, "No auto configuration classes found in META-INF/spring.factories. If you "
				+ "are using a custom packaging, make sure that file is correct.");
		return configurations;
	}

	/**
	 * Return the class used by {@link SpringFactoriesLoader} to load configuration
	 * candidates.
	 * @return the factory class
	 */
	protected Class<?> getSpringFactoriesLoaderFactoryClass() {
		return EnableAutoConfiguration.class;
	}

	private void checkExcludedClasses(List<String> configurations, Set<String> exclusions) {
		List<String> invalidExcludes = new ArrayList<>(exclusions.size());
		// TODO: 去尝试加载你配置的exclusion
		for (String exclusion : exclusions) {
			// TODO: 如果存在但不是 自动装配的类，加到invalidExcludes中
			if (ClassUtils.isPresent(exclusion, getClass().getClassLoader()) && !configurations.contains(exclusion)) {
				invalidExcludes.add(exclusion);
			}
		}
		// TODO: 到这处理，会抛出异常
		if (!invalidExcludes.isEmpty()) {
			handleInvalidExcludes(invalidExcludes);
		}
	}

	/**
	 * Handle any invalid excludes that have been specified.
	 * @param invalidExcludes the list of invalid excludes (will always have at least one
	 * element)
	 */
	protected void handleInvalidExcludes(List<String> invalidExcludes) {
		StringBuilder message = new StringBuilder();
		for (String exclude : invalidExcludes) {
			message.append("\t- ").append(exclude).append(String.format("%n"));
		}
		throw new IllegalStateException(String.format(
				"The following classes could not be excluded because they are not auto-configuration classes:%n%s",
				message));
	}

	/**
	 * Return any exclusions that limit the candidate configurations.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return exclusions or an empty set
	 */
	protected Set<String> getExclusions(AnnotationMetadata metadata, AnnotationAttributes attributes) {
		Set<String> excluded = new LinkedHashSet<>();
		// TODO: 把所有要排除的类名拿过来，然后之后进行一个排除
		excluded.addAll(asList(attributes, "exclude"));
		excluded.addAll(Arrays.asList(attributes.getStringArray("excludeName")));
		// TODO: 从环境变量中去取
		excluded.addAll(getExcludeAutoConfigurationsProperty());
		return excluded;
	}

	/**
	 * Returns the auto-configurations excluded by the
	 * {@code spring.autoconfigure.exclude} property.
	 * @return excluded auto-configurations
	 * @since 2.3.2
	 */
	protected List<String> getExcludeAutoConfigurationsProperty() {
		// TODO: 把environment拿到
		Environment environment = getEnvironment();
		if (environment == null) {
			// TODO: 如果等于空，那就不去除呗
			return Collections.emptyList();
		}
		if (environment instanceof ConfigurableEnvironment) {
			Binder binder = Binder.get(environment);
			return binder.bind(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class).map(Arrays::asList)
					.orElse(Collections.emptyList());
		}
		// TODO: 把environment中配置的 spring.autoconfigure.exclude 的value取出来
		String[] excludes = environment.getProperty(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class);
		return (excludes != null) ? Arrays.asList(excludes) : Collections.emptyList();
	}

	/**
	 * TODO: 加载AutoConfigurationImportFilter, 这里也很重要，默认的spring.factories中配置了3个filter
	 * TODO：1.OnWebApplicationCondition，2.OnClassCondition，3.OnBeanCondition
	 * @return
	 */
	protected List<AutoConfigurationImportFilter> getAutoConfigurationImportFilters() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportFilter.class, this.beanClassLoader);
	}

	private ConfigurationClassFilter getConfigurationClassFilter() {
		// TODO: 获得ConfigurationClassFilter
		if (this.configurationClassFilter == null) {
			// TODO: 把所有的AutoConfigurationImportFilter 拿到
			List<AutoConfigurationImportFilter> filters = getAutoConfigurationImportFilters();
			// TODO: 然后挨个的执行 invokeAwareMethods
			for (AutoConfigurationImportFilter filter : filters) {
				// TODO: 调用一些基本的aware的方法
				invokeAwareMethods(filter);
			}
			// TODO: 将所有的filters封装到 ConfigurationClassFilter 中
			this.configurationClassFilter = new ConfigurationClassFilter(this.beanClassLoader, filters);
		}
		return this.configurationClassFilter;
	}

	protected final <T> List<T> removeDuplicates(List<T> list) {
		// TODO: 这地方去重挺有意思的，先把list放到set里面，然后又放回了一个新的list里面
		return new ArrayList<>(new LinkedHashSet<>(list));
	}

	protected final List<String> asList(AnnotationAttributes attributes, String name) {
		String[] value = attributes.getStringArray(name);
		return Arrays.asList(value);
	}

	/**
	 * TODO: 去spring.factories中加载AutoConfigurationImportListener，默认只有一个 ConditionEvaluationReportAutoConfigurationImportListener
	 * @param configurations
	 * @param exclusions
	 */
	private void fireAutoConfigurationImportEvents(List<String> configurations, Set<String> exclusions) {
		List<AutoConfigurationImportListener> listeners = getAutoConfigurationImportListeners();
		// TODO: 如果不为空
		if (!listeners.isEmpty()) {
			// TODO: 开始构建事件
			AutoConfigurationImportEvent event = new AutoConfigurationImportEvent(this, configurations, exclusions);
			// TODO: 遍历所有的listeners, 执行invokeAwareMethod, 然后执行listener的onAutoConfigurationImportEvent()方法
			for (AutoConfigurationImportListener listener : listeners) {
				invokeAwareMethods(listener);
				listener.onAutoConfigurationImportEvent(event);
			}
		}
	}

	protected List<AutoConfigurationImportListener> getAutoConfigurationImportListeners() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportListener.class, this.beanClassLoader);
	}

	/**
	 * TODO: 设置一些基本的 aware接口的值
	 * @param instance
	 */
	private void invokeAwareMethods(Object instance) {
		if (instance instanceof Aware) {
			if (instance instanceof BeanClassLoaderAware) {
				((BeanClassLoaderAware) instance).setBeanClassLoader(this.beanClassLoader);
			}
			if (instance instanceof BeanFactoryAware) {
				((BeanFactoryAware) instance).setBeanFactory(this.beanFactory);
			}
			if (instance instanceof EnvironmentAware) {
				((EnvironmentAware) instance).setEnvironment(this.environment);
			}
			if (instance instanceof ResourceLoaderAware) {
				((ResourceLoaderAware) instance).setResourceLoader(this.resourceLoader);
			}
		}
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory);
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}

	protected final ConfigurableListableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	protected ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	protected final Environment getEnvironment() {
		return this.environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	protected final ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 1;
	}

	private static class ConfigurationClassFilter {

		private final AutoConfigurationMetadata autoConfigurationMetadata;

		/**
		 * TODO: 存储了spring.factories中的所有的filter
		 */
		private final List<AutoConfigurationImportFilter> filters;

		ConfigurationClassFilter(ClassLoader classLoader, List<AutoConfigurationImportFilter> filters) {
			this.autoConfigurationMetadata = AutoConfigurationMetadataLoader.loadMetadata(classLoader);
			this.filters = filters;
		}

		List<String> filter(List<String> configurations) {
			long startTime = System.nanoTime();
			// TODO: 转成字符串数组
			String[] candidates = StringUtils.toStringArray(configurations);
			boolean skipped = false;
			// TODO: 遍历所有的filter
			for (AutoConfigurationImportFilter filter : this.filters) {
				// TODO: 使用filter去匹配
				boolean[] match = filter.match(candidates, this.autoConfigurationMetadata);
				// TODO: 把当前filter的匹配结果拿到，然后进行遍历
				for (int i = 0; i < match.length; i++) {
					// TODO: 如果有不匹配的，就标记出一个skipped=true,然后把对应的candidates设为null
					if (!match[i]) {
						candidates[i] = null;
						skipped = true;
					}
				}
			}
			// TODO: 如果skipped还为false,那就表示，都匹配，直接返回configurations就好了
			if (!skipped) {
				return configurations;
			}
			// TODO: 否则再进行一次遍历
			List<String> result = new ArrayList<>(candidates.length);
			// TODO: 把所有的candidates拿到遍历，把不为空的加到list中
			for (String candidate : candidates) {
				if (candidate != null) {
					result.add(candidate);
				}
			}
			// TODO: 最后打印个日志，把过滤后的结果返回
			if (logger.isTraceEnabled()) {
				int numberFiltered = configurations.size() - result.size();
				logger.trace("Filtered " + numberFiltered + " auto configuration class in "
						+ TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + " ms");
			}
			return result;
		}

	}

	/**
	 * TODO: 自动配置组，实现了 DeferredImportSelector.Group
	 */
	private static class AutoConfigurationGroup
			implements DeferredImportSelector.Group, BeanClassLoaderAware, BeanFactoryAware, ResourceLoaderAware {

		private final Map<String, AnnotationMetadata> entries = new LinkedHashMap<>();

		private final List<AutoConfigurationEntry> autoConfigurationEntries = new ArrayList<>();

		private ClassLoader beanClassLoader;

		private BeanFactory beanFactory;

		private ResourceLoader resourceLoader;

		private AutoConfigurationMetadata autoConfigurationMetadata;

		@Override
		public void setBeanClassLoader(ClassLoader classLoader) {
			this.beanClassLoader = classLoader;
		}

		@Override
		public void setBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public void setResourceLoader(ResourceLoader resourceLoader) {
			this.resourceLoader = resourceLoader;
		}

		/**
		 * TODO: 处理注解以及元信息
		 * @param annotationMetadata
		 * @param deferredImportSelector
		 */
		@Override
		public void process(AnnotationMetadata annotationMetadata, DeferredImportSelector deferredImportSelector) {
			// TODO: deferredImportSelector必须是AutoConfigurationImportSelector的实例
			Assert.state(deferredImportSelector instanceof AutoConfigurationImportSelector,
					() -> String.format("Only %s implementations are supported, got %s",
							AutoConfigurationImportSelector.class.getSimpleName(),
							deferredImportSelector.getClass().getName()));
			// TODO:  获取 AutoConfigurationEntry，这里会去加载所有的自动配置类
			// TODO: AutoConfigurationEntry 中封装了所有的需要导入的配置文件，以及被排除的配置文件
			AutoConfigurationEntry autoConfigurationEntry = ((AutoConfigurationImportSelector) deferredImportSelector)
					.getAutoConfigurationEntry(annotationMetadata);
			// TODO: 加到List中保存起来
			this.autoConfigurationEntries.add(autoConfigurationEntry);
			// TODO: 把需要导入的类保存到map，key是importClassName,然后value是元信息
			for (String importClassName : autoConfigurationEntry.getConfigurations()) {
				this.entries.putIfAbsent(importClassName, annotationMetadata);
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			// TODO: 如果list中没有值，则表示没有需要导入类啊，直接返回空List
			if (this.autoConfigurationEntries.isEmpty()) {
				return Collections.emptyList();
			}
			// TODO: 把所有的排除的className拿到，进行了去重
			Set<String> allExclusions = this.autoConfigurationEntries.stream()
					.map(AutoConfigurationEntry::getExclusions).flatMap(Collection::stream).collect(Collectors.toSet());
			// TODO: 把所有的待导入的自动配置拿到，这里也进行了去重，但是使用的是 LinkedHashSet ，保证了原有的配置文件的 顺序
			Set<String> processedConfigurations = this.autoConfigurationEntries.stream()
					.map(AutoConfigurationEntry::getConfigurations).flatMap(Collection::stream)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			// TODO: 将所有的需要排除的className再次进行移除
			processedConfigurations.removeAll(allExclusions);
			// TODO: 自动配置文件 进行排序，@AutoConfigurationOrder ,@AutoConfigureAfter，@AutoConfigureBefore 这三注解在这时候生效，排好序后转成Entry然后返回过去
			return sortAutoConfigurations(processedConfigurations, getAutoConfigurationMetadata()).stream()
					.map((importClassName) -> new Entry(this.entries.get(importClassName), importClassName))
					.collect(Collectors.toList());
		}

		private AutoConfigurationMetadata getAutoConfigurationMetadata() {
			if (this.autoConfigurationMetadata == null) {
				this.autoConfigurationMetadata = AutoConfigurationMetadataLoader.loadMetadata(this.beanClassLoader);
			}
			return this.autoConfigurationMetadata;
		}

		/**
		 * TODO: 获取排序后的配置文件列表，这地方很重要，自动装配的配置文件顺序在这儿排好序
		 * @param configurations
		 * @param autoConfigurationMetadata
		 * @return
		 */
		private List<String> sortAutoConfigurations(Set<String> configurations,
				AutoConfigurationMetadata autoConfigurationMetadata) {
			return new AutoConfigurationSorter(getMetadataReaderFactory(), autoConfigurationMetadata)
					.getInPriorityOrder(configurations);
		}

		/**
		 * TODO: 从容器中把MetadataReaderFactory拿出来
		 * @return
		 */
		private MetadataReaderFactory getMetadataReaderFactory() {
			try {
				return this.beanFactory.getBean(SharedMetadataReaderFactoryContextInitializer.BEAN_NAME,
						MetadataReaderFactory.class);
			}
			catch (NoSuchBeanDefinitionException ex) {
				return new CachingMetadataReaderFactory(this.resourceLoader);
			}
		}

	}

	protected static class AutoConfigurationEntry {

		private final List<String> configurations;

		private final Set<String> exclusions;

		private AutoConfigurationEntry() {
			this.configurations = Collections.emptyList();
			this.exclusions = Collections.emptySet();
		}

		/**
		 * Create an entry with the configurations that were contributed and their
		 * exclusions.
		 * @param configurations the configurations that should be imported
		 * @param exclusions the exclusions that were applied to the original list
		 */
		AutoConfigurationEntry(Collection<String> configurations, Collection<String> exclusions) {
			this.configurations = new ArrayList<>(configurations);
			this.exclusions = new HashSet<>(exclusions);
		}

		public List<String> getConfigurations() {
			return this.configurations;
		}

		public Set<String> getExclusions() {
			return this.exclusions;
		}

	}

}
