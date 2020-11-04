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

package org.springframework.boot.autoconfigure.condition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Abstract base class for a {@link SpringBootCondition} that also implements
 * {@link AutoConfigurationImportFilter}.
 *
 * @author Phillip Webb
 */
abstract class FilteringSpringBootCondition extends SpringBootCondition
		implements AutoConfigurationImportFilter, BeanFactoryAware, BeanClassLoaderAware {

	private BeanFactory beanFactory;

	private ClassLoader beanClassLoader;

	@Override
	public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
		ConditionEvaluationReport report = ConditionEvaluationReport.find(this.beanFactory);
		// TODO: 对所有的自动装配的配置文件，进行一个判断，然后输出结果返回到一个数组中，这里分两种情况去处理
		// TODO: 如果autoConfigurationClasses的长度大于0，并且你处理器的个数大于1，则开一个线程和主线程一起去计算，否则，主线程去判断
		ConditionOutcome[] outcomes = getOutcomes(autoConfigurationClasses, autoConfigurationMetadata);
		boolean[] match = new boolean[outcomes.length];
		for (int i = 0; i < outcomes.length; i++) {
			// TODO: 结果进行整理，如果返回null,或者isMatch是true, OK那就是true, 否则是false
			match[i] = (outcomes[i] == null || outcomes[i].isMatch());
			// TODO: 如果不匹配，打印个日志，把不匹配的日志打印出来
			if (!match[i] && outcomes[i] != null) {
				// TODO: 打印日志
				logOutcome(autoConfigurationClasses[i], outcomes[i]);
				if (report != null) {
					// TODO: 存到 ConditionEvaluationReport 中
					report.recordConditionEvaluation(autoConfigurationClasses[i], this, outcomes[i]);
				}
			}
		}
		return match;
	}

	protected abstract ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata);

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	protected final BeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	protected final ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	protected final List<String> filter(Collection<String> classNames, ClassNameFilter classNameFilter,
			ClassLoader classLoader) {
		if (CollectionUtils.isEmpty(classNames)) {
			return Collections.emptyList();
		}
		List<String> matches = new ArrayList<>(classNames.size());
		for (String candidate : classNames) {
			// TODO: 这里 如果匹配，就加到matches中
			if (classNameFilter.matches(candidate, classLoader)) {
				matches.add(candidate);
			}
		}
		// TODO: 最后统一返回
		return matches;
	}

	/**
	 * Slightly faster variant of {@link ClassUtils#forName(String, ClassLoader)} that
	 * doesn't deal with primitives, arrays or inner types.
	 * @param className the class name to resolve
	 * @param classLoader the class loader to use
	 * @return a resolved class
	 * @throws ClassNotFoundException if the class cannot be found
	 */
	protected static Class<?> resolve(String className, ClassLoader classLoader) throws ClassNotFoundException {
		if (classLoader != null) {
			return Class.forName(className, false, classLoader);
		}
		return Class.forName(className);
	}

	/**
	 * TODO: 给你一个className判断当前的classpath中是否存在这个class, 这里的枚举挺有意思的
	 */
	protected enum ClassNameFilter {

		/**
		 * TODO: 两种情况存在或者是不存在
		 */
		PRESENT {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return isPresent(className, classLoader);
			}

		},

		/**
		 * 这里相当于搞了两个实现类
		 */
		MISSING {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return !isPresent(className, classLoader);
			}

		};

		abstract boolean matches(String className, ClassLoader classLoader);

		static boolean isPresent(String className, ClassLoader classLoader) {
			// TODO: 获取classLoader
			if (classLoader == null) {
				classLoader = ClassUtils.getDefaultClassLoader();
			}
			try {
				// TODO: 尝试去类加载
				resolve(className, classLoader);
				// TODO: 加载到了返回true, 否则返回false
				return true;
			}
			catch (Throwable ex) {
				return false;
			}
		}

	}

}
