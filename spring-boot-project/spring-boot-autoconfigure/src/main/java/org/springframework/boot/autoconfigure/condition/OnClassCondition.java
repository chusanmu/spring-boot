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

package org.springframework.boot.autoconfigure.condition;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * {@link Condition} and {@link AutoConfigurationImportFilter} that checks for the
 * presence or absence of specific classes.
 *
 * @author Phillip Webb
 * @see ConditionalOnClass
 * @see ConditionalOnMissingClass
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class OnClassCondition extends FilteringSpringBootCondition {

	@Override
	protected final ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// Split the work and perform half in a background thread if more than one
		// processor is available. Using a single additional thread seems to offer the
		// best performance. More threads make things worse.
		// TODO: 这里考虑了进行任务的分隔，如果你的配置文件个数大于1，并且处理器个数大于1
		if (autoConfigurationClasses.length > 1 && Runtime.getRuntime().availableProcessors() > 1) {
			// TODO: 这里进行新开线程去处理
			return resolveOutcomesThreaded(autoConfigurationClasses, autoConfigurationMetadata);
		}
		else {
			// TODO: 否则直接去处理
			OutcomesResolver outcomesResolver = new StandardOutcomesResolver(autoConfigurationClasses, 0,
					autoConfigurationClasses.length, autoConfigurationMetadata, getBeanClassLoader());
			// TODO: 直接拿到解析结果
			return outcomesResolver.resolveOutcomes();
		}
	}

	private ConditionOutcome[] resolveOutcomesThreaded(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// TODO: 这里挺有意思的，将所有的配置文件一分为二
		int split = autoConfigurationClasses.length / 2;
		// TODO: 创建一个 OutcomesResolver，然后交给一个线程去处理, 他把全部的autoConfigurationClasses，分成了两半
		// TODO: 第一部分 用一个线程去处理
		OutcomesResolver firstHalfResolver = createOutcomesResolver(autoConfigurationClasses, 0, split,
				autoConfigurationMetadata);
		// TODO: 第二部分依旧主线程去处理
		OutcomesResolver secondHalfResolver = new StandardOutcomesResolver(autoConfigurationClasses, split,
				autoConfigurationClasses.length, autoConfigurationMetadata, getBeanClassLoader());
		// TODO: 第二部分去拿它的输出结果
		ConditionOutcome[] secondHalf = secondHalfResolver.resolveOutcomes();
		// TODO: 然后第一部分，线程那部分也去拿输出结果
		ConditionOutcome[] firstHalf = firstHalfResolver.resolveOutcomes();
		// TODO: 用来收集结果
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		// TODO: 把两次输出结果，分别拷贝到outcomes数组中
		System.arraycopy(firstHalf, 0, outcomes, 0, firstHalf.length);
		System.arraycopy(secondHalf, 0, outcomes, split, secondHalf.length);
		return outcomes;
	}

	private OutcomesResolver createOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// TODO: 创建了一个标准的StandardOutcomesResolver，然后交给了一个线程去处理
		OutcomesResolver outcomesResolver = new StandardOutcomesResolver(autoConfigurationClasses, start, end,
				autoConfigurationMetadata, getBeanClassLoader());
		try {
			// TODO: 交给了一个线程去处理
			return new ThreadedOutcomesResolver(outcomesResolver);
		}
		catch (AccessControlException ex) {
			return outcomesResolver;
		}
	}

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		// TODO: 获取当前的classLoader, 然后构建一个空的ConditionMessage
		ClassLoader classLoader = context.getClassLoader();
		ConditionMessage matchMessage = ConditionMessage.empty();
		// TODO: 这里拿到的是className集合
		List<String> onClasses = getCandidates(metadata, ConditionalOnClass.class);
		if (onClasses != null) {
			// TODO: 这里去判断下，当前classpath下是否没有，把没有的收集过来
			List<String> missing = filter(onClasses, ClassNameFilter.MISSING, classLoader);
			if (!missing.isEmpty()) {
				// TODO: 这里就开始构造日志信息了，不匹配，直接返回
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
						.didNotFind("required class", "required classes").items(Style.QUOTE, missing));
			}
			// TODO: 这里去构建存在class的情况
			matchMessage = matchMessage.andCondition(ConditionalOnClass.class)
					.found("required class", "required classes")
					// TODO: filter变成了ClassNameFilter.PRESENT
					.items(Style.QUOTE, filter(onClasses, ClassNameFilter.PRESENT, classLoader));
		}
		// TODO: 尝试拿到 ConditionalOnMissingClass 这个注解
		List<String> onMissingClasses = getCandidates(metadata, ConditionalOnMissingClass.class);
		// TODO: 如果 存在
		if (onMissingClasses != null) {
			// TODO: 去加载下，看看有没有
			List<String> present = filter(onMissingClasses, ClassNameFilter.PRESENT, classLoader);
			// TODO: 加载到了
			if (!present.isEmpty()) {
				// TODO: 到这里其实就是不匹配，然后直接返回就ok了
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnMissingClass.class)
						.found("unwanted class", "unwanted classes").items(Style.QUOTE, present));
			}
			// TODO: 更换filter继续尝试过滤
			matchMessage = matchMessage.andCondition(ConditionalOnMissingClass.class)
					.didNotFind("unwanted class", "unwanted classes")
					.items(Style.QUOTE, filter(onMissingClasses, ClassNameFilter.MISSING, classLoader));
		}
		// TODO: 最后走到这里，就是匹配，则构建匹配消息
		return ConditionOutcome.match(matchMessage);
	}

	private List<String> getCandidates(AnnotatedTypeMetadata metadata, Class<?> annotationType) {
		// TODO: 拿到当前注解的所有的属性及值
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(annotationType.getName(), true);
		if (attributes == null) {
			return null;
		}
		List<String> candidates = new ArrayList<>();
		addAll(candidates, attributes.get("value"));
		addAll(candidates, attributes.get("name"));
		return candidates;
	}

	private void addAll(List<String> list, List<Object> itemsToAdd) {
		if (itemsToAdd != null) {
			for (Object item : itemsToAdd) {
				Collections.addAll(list, (String[]) item);
			}
		}
	}

	private interface OutcomesResolver {

		ConditionOutcome[] resolveOutcomes();

	}

	private static final class ThreadedOutcomesResolver implements OutcomesResolver {

		private final Thread thread;

		private volatile ConditionOutcome[] outcomes;

		private ThreadedOutcomesResolver(OutcomesResolver outcomesResolver) {
			// TODO: 交给了一个线程去处理，之后启动这个线程
			this.thread = new Thread(() -> this.outcomes = outcomesResolver.resolveOutcomes());
			this.thread.start();
		}

		@Override
		public ConditionOutcome[] resolveOutcomes() {
			try {
				// TODO: 主线程调用了thread.join,等待thread线程去执行完毕，然后把结果输出
				this.thread.join();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return this.outcomes;
		}

	}

	private static final class StandardOutcomesResolver implements OutcomesResolver {

		private final String[] autoConfigurationClasses;

		private final int start;

		private final int end;

		private final AutoConfigurationMetadata autoConfigurationMetadata;

		private final ClassLoader beanClassLoader;

		private StandardOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata, ClassLoader beanClassLoader) {
			this.autoConfigurationClasses = autoConfigurationClasses;
			this.start = start;
			this.end = end;
			this.autoConfigurationMetadata = autoConfigurationMetadata;
			this.beanClassLoader = beanClassLoader;
		}

		/**
		 * TODO: 解析配置文件，这里有个起始角标，判断它是否合法，需要导入
		 * @return
		 */
		@Override
		public ConditionOutcome[] resolveOutcomes() {
			return getOutcomes(this.autoConfigurationClasses, this.start, this.end, this.autoConfigurationMetadata);
		}

		private ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata) {
			// TODO: 创建一个指定长度的数组
			ConditionOutcome[] outcomes = new ConditionOutcome[end - start];
			for (int i = start; i < end; i++) {
				// TODO: 拿到具体的配置文件
				String autoConfigurationClass = autoConfigurationClasses[i];
				if (autoConfigurationClass != null) {
					// TODO: 如果该class不为空，则去获取 它的 ConditionalOnClass 注解 的 属性
					String candidates = autoConfigurationMetadata.get(autoConfigurationClass, "ConditionalOnClass");
					// TODO: 如果它的这个属性也不为空
					if (candidates != null) {
						// TODO: 记录输出
						outcomes[i - start] = getOutcome(candidates);
					}
				}
			}
			return outcomes;
		}

		private ConditionOutcome getOutcome(String candidates) {
			try {
				// TODO: 看它包不包含逗号，不包含逗号就直接去处理
				if (!candidates.contains(",")) {
					return getOutcome(candidates, this.beanClassLoader);
				}
				// TODO: 否则就用逗号拆分一下，然后去遍历处理
				for (String candidate : StringUtils.commaDelimitedListToStringArray(candidates)) {
					ConditionOutcome outcome = getOutcome(candidate, this.beanClassLoader);
					if (outcome != null) {
						// TODO: 直接返回结果
						return outcome;
					}
				}
			}
			catch (Exception ex) {
				// We'll get another chance later
			}
			return null;
		}

		private ConditionOutcome getOutcome(String className, ClassLoader classLoader) {
			// TODO: 去尝试匹配，如果它是缺少的，返回了true , 那就是不匹配呗
			if (ClassNameFilter.MISSING.matches(className, classLoader)) {
				// TODO: 把不匹配结果输出
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
						.didNotFind("required class").items(Style.QUOTE, className));
			}
			return null;
		}

	}

}
