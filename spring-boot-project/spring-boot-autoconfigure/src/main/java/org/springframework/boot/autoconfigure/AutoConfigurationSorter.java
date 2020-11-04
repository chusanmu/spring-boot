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

package org.springframework.boot.autoconfigure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.Assert;

/**
 * Sort {@link EnableAutoConfiguration auto-configuration} classes into priority order by
 * reading {@link AutoConfigureOrder @AutoConfigureOrder},
 * {@link AutoConfigureBefore @AutoConfigureBefore} and
 * {@link AutoConfigureAfter @AutoConfigureAfter} annotations (without loading classes).
 *
 * @author Phillip Webb
 */
class AutoConfigurationSorter {

	private final MetadataReaderFactory metadataReaderFactory;

	private final AutoConfigurationMetadata autoConfigurationMetadata;

	AutoConfigurationSorter(MetadataReaderFactory metadataReaderFactory,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		Assert.notNull(metadataReaderFactory, "MetadataReaderFactory must not be null");
		this.metadataReaderFactory = metadataReaderFactory;
		this.autoConfigurationMetadata = autoConfigurationMetadata;
	}

	/**
	 * TODO: 获取排好序的自动装配配置列表
	 *
	 * @param classNames
	 * @return
	 */
	List<String> getInPriorityOrder(Collection<String> classNames) {
		// TODO: AutoConfigurationClasses 此类主要的作用就是解析元数据，spring-autoconfigure-metadata.properties读取元信息
		AutoConfigurationClasses classes = new AutoConfigurationClasses(this.metadataReaderFactory,
				this.autoConfigurationMetadata, classNames);
		// TODO: 创建一个新的list
		List<String> orderedClassNames = new ArrayList<>(classNames);
		// Initially sort alphabetically
		// TODO: 首次排序，进行排序，这里首次排序是根据name去排序的，根据自然顺序排序，例如 a,b,c 这样...
		Collections.sort(orderedClassNames);
		// Then sort by order
		// TODO: 第二轮排序，根据AutoConfigureOrder进行排序，优先级越高的，排在前面
		orderedClassNames.sort((o1, o2) -> {
			int i1 = classes.get(o1).getOrder();
			int i2 = classes.get(o2).getOrder();
			return Integer.compare(i1, i2);
		});
		// Then respect @AutoConfigureBefore @AutoConfigureAfter
		// TODO: 第三轮排序 根据  @AutoConfigureBefore @AutoConfigureAfter 这俩注解进行排序
		orderedClassNames = sortByAnnotation(classes, orderedClassNames);
		// TODO: 最后把排好序的names返回回去
		return orderedClassNames;
	}

	/**
	 * TODO: 根据 @AutoConfigureBefore @AutoConfigureAfter 这俩注解进行排序
	 * @param classes
	 * @param classNames
	 * @return
	 */
	private List<String> sortByAnnotation(AutoConfigurationClasses classes, List<String> classNames) {
		List<String> toSort = new ArrayList<>(classNames);
		toSort.addAll(classes.getAllNames());
		Set<String> sorted = new LinkedHashSet<>();
		Set<String> processing = new LinkedHashSet<>();
		// TODO: 只要toSort不为空，就去处理排序
		while (!toSort.isEmpty()) {
			// TODO: 根据注解进行排序
			doSortByAfterAnnotation(classes, toSort, sorted, processing, null);
		}
		sorted.retainAll(classNames);
		return new ArrayList<>(sorted);
	}

	private void doSortByAfterAnnotation(AutoConfigurationClasses classes, List<String> toSort, Set<String> sorted,
			Set<String> processing, String current) {
		if (current == null) {
			current = toSort.remove(0);
		}
		processing.add(current);
		for (String after : classes.getClassesRequestedAfter(current)) {
			Assert.state(!processing.contains(after),
					"AutoConfigure cycle detected between " + current + " and " + after);
			if (!sorted.contains(after) && toSort.contains(after)) {
				doSortByAfterAnnotation(classes, toSort, sorted, processing, after);
			}
		}
		processing.remove(current);
		sorted.add(current);
	}

	private static class AutoConfigurationClasses {

		private final Map<String, AutoConfigurationClass> classes = new HashMap<>();

		AutoConfigurationClasses(MetadataReaderFactory metadataReaderFactory,
				AutoConfigurationMetadata autoConfigurationMetadata, Collection<String> classNames) {
			addToClasses(metadataReaderFactory, autoConfigurationMetadata, classNames, true);
		}

		Set<String> getAllNames() {
			// TODO: 拿到所有的需要自动装配的类
			return this.classes.keySet();
		}

		private void addToClasses(MetadataReaderFactory metadataReaderFactory,
				AutoConfigurationMetadata autoConfigurationMetadata, Collection<String> classNames, boolean required) {
			// TODO: 把所有的classNames进行遍历
			for (String className : classNames) {
				if (!this.classes.containsKey(className)) {
					AutoConfigurationClass autoConfigurationClass = new AutoConfigurationClass(className,
							metadataReaderFactory, autoConfigurationMetadata);
					boolean available = autoConfigurationClass.isAvailable();
					// TODO: 只要能找到，或者是必须的，就放到classes中缓存起来
					if (required || available) {
						this.classes.put(className, autoConfigurationClass);
					}
					// TODO: 这里相当于一个递归，二分查找处理
					if (available) {
						addToClasses(metadataReaderFactory, autoConfigurationMetadata,
								autoConfigurationClass.getBefore(), false);
						addToClasses(metadataReaderFactory, autoConfigurationMetadata,
								autoConfigurationClass.getAfter(), false);
					}
				}
			}
		}

		AutoConfigurationClass get(String className) {
			return this.classes.get(className);
		}

		Set<String> getClassesRequestedAfter(String className) {
			Set<String> classesRequestedAfter = new LinkedHashSet<>(get(className).getAfter());
			this.classes.forEach((name, autoConfigurationClass) -> {
				if (autoConfigurationClass.getBefore().contains(className)) {
					classesRequestedAfter.add(name);
				}
			});
			return classesRequestedAfter;
		}

	}

	/**
	 * TODO: 维护了自动装配class的元信息吧
	 *
	 */
	private static class AutoConfigurationClass {

		/**
		 * TODO: 当前自动装配的全类名
		 */
		private final String className;

		/**
		 * TODO: metadataReaderFactory用来获取metadataReader
		 */
		private final MetadataReaderFactory metadataReaderFactory;

		/**
		 * TODO: 维护了当前自动配置类的元信息
		 */
		private final AutoConfigurationMetadata autoConfigurationMetadata;

		/**
		 * TODO: 维护了当前自动配置类的注解元信息
		 */
		private volatile AnnotationMetadata annotationMetadata;

		/**
		 * TODO: 保存了需要在它之前进行自动配置的类
		 */
		private volatile Set<String> before;

		/**
		 * TODO: 保存了需要在它之后进行自动配置的类
		 */
		private volatile Set<String> after;

		AutoConfigurationClass(String className, MetadataReaderFactory metadataReaderFactory,
				AutoConfigurationMetadata autoConfigurationMetadata) {
			this.className = className;
			this.metadataReaderFactory = metadataReaderFactory;
			this.autoConfigurationMetadata = autoConfigurationMetadata;
		}

		boolean isAvailable() {
			try {
				if (!wasProcessed()) {
					getAnnotationMetadata();
				}
				return true;
			}
			catch (Exception ex) {
				return false;
			}
		}

		Set<String> getBefore() {
			if (this.before == null) {
				// TODO: 如果元信息配置文件中包含这个class,则从元信息中读取 这个className对应的 AutoConfigureBefore对应的类名然后返回，否则就去解析
				// TODO: 这个类上 注解里面对应的值
				this.before = (wasProcessed() ? this.autoConfigurationMetadata.getSet(this.className,
						"AutoConfigureBefore", Collections.emptySet()) : getAnnotationValue(AutoConfigureBefore.class));
			}
			return this.before;
		}

		Set<String> getAfter() {
			if (this.after == null) {
				this.after = (wasProcessed() ? this.autoConfigurationMetadata.getSet(this.className,
						"AutoConfigureAfter", Collections.emptySet()) : getAnnotationValue(AutoConfigureAfter.class));
			}
			return this.after;
		}

		/**
		 * TODO: 获取order, 先尝试从元信息中获取，如果元信息中没有，则从当前配置类的 @AutoConfigureOrder 中获取
		 * @return
		 */
		private int getOrder() {
			if (wasProcessed()) {
				return this.autoConfigurationMetadata.getInteger(this.className, "AutoConfigureOrder",
						AutoConfigureOrder.DEFAULT_ORDER);
			}
			// TODO: 直接从注解中拿
			Map<String, Object> attributes = getAnnotationMetadata()
					.getAnnotationAttributes(AutoConfigureOrder.class.getName());
			return (attributes != null) ? (Integer) attributes.get("value") : AutoConfigureOrder.DEFAULT_ORDER;
		}

		private boolean wasProcessed() {
			// TODO: autoConfigurationMetadata 不等于空，并且 autoConfigurationMetadata中存在这个className代表的key
			return (this.autoConfigurationMetadata != null
					&& this.autoConfigurationMetadata.wasProcessed(this.className));
		}

		/**
		 * TODO: 获取注解里面的值
		 * @param annotation
		 * @return
		 */
		private Set<String> getAnnotationValue(Class<?> annotation) {
			// TODO: 获取  AutoConfigureAfter AutoConfigureBefore 里面的值，这里 注意，第二个参数传了个true，表示class会转为string
			Map<String, Object> attributes = getAnnotationMetadata().getAnnotationAttributes(annotation.getName(),
					true);
			if (attributes == null) {
				return Collections.emptySet();
			}
			Set<String> value = new LinkedHashSet<>();
			// TODO: 把属性值加进去
			Collections.addAll(value, (String[]) attributes.get("value"));
			Collections.addAll(value, (String[]) attributes.get("name"));
			return value;
		}

		/**
		 * TODO: 获取注解元信息，先拿到MetadataReader,  然后利用metadataReader获取annotationMetadata
		 *
		 * @return
		 */
		private AnnotationMetadata getAnnotationMetadata() {
			if (this.annotationMetadata == null) {
				try {
					MetadataReader metadataReader = this.metadataReaderFactory.getMetadataReader(this.className);
					this.annotationMetadata = metadataReader.getAnnotationMetadata();
				}
				catch (IOException ex) {
					throw new IllegalStateException("Unable to read meta-data for class " + this.className, ex);
				}
			}
			return this.annotationMetadata;
		}

	}

}
