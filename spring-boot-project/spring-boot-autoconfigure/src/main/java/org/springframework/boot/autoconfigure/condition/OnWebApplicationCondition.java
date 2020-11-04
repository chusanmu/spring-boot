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

import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.reactive.context.ConfigurableReactiveWebEnvironment;
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@link Condition} that checks for the presence or absence of
 * {@link WebApplicationContext}.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @see ConditionalOnWebApplication
 * @see ConditionalOnNotWebApplication
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class OnWebApplicationCondition extends FilteringSpringBootCondition {

	private static final String SERVLET_WEB_APPLICATION_CLASS = "org.springframework.web.context.support.GenericWebApplicationContext";

	private static final String REACTIVE_WEB_APPLICATION_CLASS = "org.springframework.web.reactive.HandlerResult";

	@Override
	protected ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// TODO: 创建输出结果集
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		for (int i = 0; i < outcomes.length; i++) {
			String autoConfigurationClass = autoConfigurationClasses[i];
			// TODO: 拿出来之后，挨个的去判断
			if (autoConfigurationClass != null) {
				// TODO: 挨个的去判断，然后把判断结果放在固定的位置, 返回null表示匹配
				outcomes[i] = getOutcome(
						autoConfigurationMetadata.get(autoConfigurationClass, "ConditionalOnWebApplication"));
			}
		}
		// TODO: 最后把结果集返回
		return outcomes;
	}

	private ConditionOutcome getOutcome(String type) {
		// TODO: 如果type为空，直接返回null,表示符合条件
		if (type == null) {
			return null;
		}
		ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnWebApplication.class);
		// TODO: 如果你依赖servlet环境，但是并不存在servlet相关的class，ok，那不匹配
		if (ConditionalOnWebApplication.Type.SERVLET.name().equals(type)) {
			if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS, getBeanClassLoader())) {
				// TODO: 返回不匹配结果
				return ConditionOutcome.noMatch(message.didNotFind("servlet web application classes").atAll());
			}
		}
		// TODO: 如果你依赖reactive环境，但是并不存在reactive环境相关的class，ok,那也返回不匹配结果
		if (ConditionalOnWebApplication.Type.REACTIVE.name().equals(type)) {
			if (!ClassNameFilter.isPresent(REACTIVE_WEB_APPLICATION_CLASS, getBeanClassLoader())) {
				return ConditionOutcome.noMatch(message.didNotFind("reactive web application classes").atAll());
			}
		}
		// TODO: 如果既没有servlet环境 也 没有reactive环境，那就报个不匹配吧，毕竟啥都没有
		if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS, getBeanClassLoader())
				&& !ClassUtils.isPresent(REACTIVE_WEB_APPLICATION_CLASS, getBeanClassLoader())) {
			return ConditionOutcome.noMatch(message.didNotFind("reactive or servlet web application classes").atAll());
		}
		// TODO: 最后匹配返回null
		return null;
	}

	/**
	 * TODO: 匹配运行环境
	 */
	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		// TODO: 如果标注的有 ConditionalOnWebApplication这个注解，一般走到这里都会存在 ConditionalOnWebApplication，requrired一般为true
		boolean required = metadata.isAnnotated(ConditionalOnWebApplication.class.getName());
		// TODO: 然后就去判断是不是web环境
		ConditionOutcome outcome = isWebApplication(context, metadata, required);
		// TODO: 如果  存在注解，是必须的 但是没有匹配上，那就返回noMatch
		if (required && !outcome.isMatch()) {
			return ConditionOutcome.noMatch(outcome.getConditionMessage());
		}
		// TODO: 如果不是required 但是匹配上了，那返回 noMatch
		if (!required && outcome.isMatch()) {
			return ConditionOutcome.noMatch(outcome.getConditionMessage());
		}
		// TODO: 最后走到这，返回match
		return ConditionOutcome.match(outcome.getConditionMessage());
	}

	private ConditionOutcome isWebApplication(ConditionContext context, AnnotatedTypeMetadata metadata,
			boolean required) {
		// TODO: 去取依赖类型
		switch (deduceType(metadata)) {
			// TODO: 如果是servlet环境，就去判断是否满足条件
		case SERVLET:
			return isServletWebApplication(context);
			// TODO: 判断是不是 REACTIVE 环境
		case REACTIVE:
			return isReactiveWebApplication(context);
		default:
			// TODO: 否则是任意一个 都可以
			return isAnyWebApplication(context, required);
		}
	}

	/**
	 * TODO：匹配任意一个web容器
	 * @param context
	 * @param required
	 * @return
	 */
	private ConditionOutcome isAnyWebApplication(ConditionContext context, boolean required) {
		ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnWebApplication.class,
				required ? "(required)" : "");
		// TODO: 看看是不是servlet 环境
		ConditionOutcome servletOutcome = isServletWebApplication(context);
		// TODO: 如果匹配上了
		if (servletOutcome.isMatch() && required) {
			// TODO: 返回匹配信息
			return new ConditionOutcome(servletOutcome.isMatch(), message.because(servletOutcome.getMessage()));
		}
		// TODO: 判断是不是 reactive 环境
		ConditionOutcome reactiveOutcome = isReactiveWebApplication(context);
		// TODO: 匹配上了 返回匹配信息
		if (reactiveOutcome.isMatch() && required) {
			return new ConditionOutcome(reactiveOutcome.isMatch(), message.because(reactiveOutcome.getMessage()));
		}
		// TODO: 如果到这，它俩有任意一个返回true, 那就是返回匹配上了，否则，都没匹配上
		return new ConditionOutcome(servletOutcome.isMatch() || reactiveOutcome.isMatch(),
				message.because(servletOutcome.getMessage()).append("and").append(reactiveOutcome.getMessage()));
	}

	/**
	 * TODO: 判断是否是servlet环境
	 * @param context
	 * @return
	 */
	private ConditionOutcome isServletWebApplication(ConditionContext context) {
		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		// TODO: 如果不存在servlet环境所必须的class, 也就是GenericWebApplicationContext ，如果不是这个容器，那就不是servlet环境
		if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS, context.getClassLoader())) {
			return ConditionOutcome.noMatch(message.didNotFind("servlet web application classes").atAll());
		}
		// TODO: 看看有没有作用域是session类型的bean，如果有，则也算是匹配，直接返回
		if (context.getBeanFactory() != null) {
			String[] scopes = context.getBeanFactory().getRegisteredScopeNames();
			if (ObjectUtils.containsElement(scopes, "session")) {
				return ConditionOutcome.match(message.foundExactly("'session' scope"));
			}
		}
		// TODO: 看看当前容器的环境类型, 如果是web环境的容器，那也匹配上了
		if (context.getEnvironment() instanceof ConfigurableWebEnvironment) {
			return ConditionOutcome.match(message.foundExactly("ConfigurableWebEnvironment"));
		}
		// TODO: 判断当前的资源加载器，如果是WebApplicationContext，也算是匹配上了
		if (context.getResourceLoader() instanceof WebApplicationContext) {
			return ConditionOutcome.match(message.foundExactly("WebApplicationContext"));
		}
		// TODO: 最后 走到这 就是没匹配
		return ConditionOutcome.noMatch(message.because("not a servlet web application"));
	}

	private ConditionOutcome isReactiveWebApplication(ConditionContext context) {
		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		// TODO: 看看其存不存在所必须的类，如果不存在，就直接返回不匹配得了
		if (!ClassNameFilter.isPresent(REACTIVE_WEB_APPLICATION_CLASS, context.getClassLoader())) {
			return ConditionOutcome.noMatch(message.didNotFind("reactive web application classes").atAll());
		}
		// TODO: 如果环境类型是 ConfigurableReactiveWebEnvironment 也算是匹配上了
		if (context.getEnvironment() instanceof ConfigurableReactiveWebEnvironment) {
			return ConditionOutcome.match(message.foundExactly("ConfigurableReactiveWebEnvironment"));
		}
		// TODO: 如果资源加载器 是 ReactiveWebApplicationContext 类型的 也算是匹配上了
		if (context.getResourceLoader() instanceof ReactiveWebApplicationContext) {
			return ConditionOutcome.match(message.foundExactly("ReactiveWebApplicationContext"));
		}
		// TODO: 否则没匹配上
		return ConditionOutcome.noMatch(message.because("not a reactive web application"));
	}

	private Type deduceType(AnnotatedTypeMetadata metadata) {
		// TODO: 拿取里面的type属性，看看是取Servlet还是REACTIVE
		Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnWebApplication.class.getName());
		// TODO: 如果 attributes不为空，取里面的type
		if (attributes != null) {
			return (Type) attributes.get("type");
		}
		// TODO: 否则任意类型都可以
		return Type.ANY;
	}

}
